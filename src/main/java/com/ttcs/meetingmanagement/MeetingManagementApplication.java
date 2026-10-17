package com.ttcs.meetingmanagement;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.OffsetDateTime;
import java.util.*;

@SpringBootApplication
public class MeetingManagementApplication {
    public static void main(String[] args){SpringApplication.run(MeetingManagementApplication.class,args);}
    @Bean BCryptPasswordEncoder encoder(){return new BCryptPasswordEncoder();}
}

@JsonAutoDetect(fieldVisibility=JsonAutoDetect.Visibility.ANY)
@Entity @Table(name="roles") class Role {
    @Id @Column(name="role_id",length=36) String id;
    @Column(name="role_name",nullable=false,unique=true) String name;
    protected Role(){} Role(String id,String name){this.id=id;this.name=name;}
}
@JsonAutoDetect(fieldVisibility=JsonAutoDetect.Visibility.ANY)
@Entity @Table(name="departments",uniqueConstraints=@UniqueConstraint(name="uk_department_hrm_code",columnNames="hrm_code")) class Department {
    @Id @Column(name="department_id",length=36) String id;
    @Column(name="department_name",nullable=false) String name;
    @Column(name="hrm_code",length=100) String hrmCode;
    @Column(name="last_synced_at") OffsetDateTime lastSyncedAt;
    protected Department(){} Department(String id,String name){this.id=id;this.name=name;}
}
@Entity @Table(name="users",uniqueConstraints={@UniqueConstraint(name="uk_user_hrm_code",columnNames="hrm_code"),@UniqueConstraint(name="uk_user_email",columnNames="email")}) class User {
    @Id @Column(name="user_id",length=36) String id;
    @Column(name="role_id",nullable=false,length=36) String roleId;
    @Column(name="department_id",length=36) String departmentId;
    @Column(name="hrm_code",length=100) String hrmCode;
    @Column(name="full_name",nullable=false) String fullName;
    @Column(nullable=false) String email; String phone;
    @Column(nullable=false) String status="ACTIVE";
    @Column(name="password_hash") String passwordHash;
    @Column(name="created_at",nullable=false) OffsetDateTime createdAt;
    @Column(name="updated_at",nullable=false) OffsetDateTime updatedAt;
    @Column(name="last_synced_at") OffsetDateTime lastSyncedAt;
    @PrePersist void create(){if(id==null)id=UUID.randomUUID().toString();createdAt=updatedAt=OffsetDateTime.now();}
    @PreUpdate void update(){updatedAt=OffsetDateTime.now();}
}

interface RoleRepository extends JpaRepository<Role,String>{}
interface DepartmentRepository extends JpaRepository<Department,String>{}
interface UserRepository extends JpaRepository<User,String>{
    boolean existsByEmailIgnoreCase(String email);
    Optional<User> findByEmailIgnoreCase(String email);
}

record CreateUserRequest(@NotBlank String fullName,@Email @NotBlank String email,
        @Pattern(regexp="^$|^[0-9+() .-]{7,20}$") String phone,
        @NotBlank @Size(min=8,max=72) String password,@NotBlank String roleId,String departmentId){}
record UpdateUserRequest(@NotBlank String fullName,@Email @NotBlank String email,
        @Pattern(regexp="^$|^[0-9+() .-]{7,20}$") String phone,@NotBlank String roleId,String departmentId){}
record UserResponse(String id,String fullName,String email,String phone,String status,String roleId,String departmentId,OffsetDateTime createdAt){
    static UserResponse of(User u){return new UserResponse(u.id,u.fullName,u.email,u.phone,u.status,u.roleId,u.departmentId,u.createdAt);}
}

@Service class UserService {
    final UserRepository users; final RoleRepository roles; final DepartmentRepository departments; final BCryptPasswordEncoder encoder;
    UserService(UserRepository u,RoleRepository r,DepartmentRepository d,BCryptPasswordEncoder e){users=u;roles=r;departments=d;encoder=e;}
    @Transactional UserResponse create(CreateUserRequest r){
        String email=normalize(r.email());if(users.existsByEmailIgnoreCase(email))throw conflict("Email already exists");validate(r.roleId(),r.departmentId());
        User u=new User();u.fullName=r.fullName().trim();u.email=email;u.phone=r.phone();u.passwordHash=encoder.encode(r.password());u.roleId=r.roleId();u.departmentId=blank(r.departmentId());return UserResponse.of(users.save(u));
    }
    @Transactional UserResponse update(String id,UpdateUserRequest r){
        User u=get(id);String email=normalize(r.email());users.findByEmailIgnoreCase(email).filter(x->!x.id.equals(id)).ifPresent(x->{throw conflict("Email already exists");});validate(r.roleId(),r.departmentId());
        u.fullName=r.fullName().trim();u.email=email;u.phone=r.phone();u.roleId=r.roleId();u.departmentId=blank(r.departmentId());return UserResponse.of(users.save(u));
    }
    @Transactional UserResponse lock(String id,boolean locked){User u=get(id);u.status=locked?"LOCKED":"ACTIVE";return UserResponse.of(users.save(u));}
    @Transactional(readOnly=true) UserResponse one(String id){return UserResponse.of(get(id));}
    @Transactional(readOnly=true) void requireAdmin(String id){User u=get(id);Role role=roles.findById(u.roleId).orElseThrow(UserService::forbidden);if(!"ACTIVE".equalsIgnoreCase(u.status)||!"Admin".equalsIgnoreCase(role.name))throw forbidden();}
    private User get(String id){return users.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"User not found"));}
    private void validate(String role,String department){if(!Set.of("ADMIN","PM","MEMBER").contains(role)||!roles.existsById(role))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Role must be ADMIN, PM or MEMBER");if(department!=null&&!department.isBlank()&&!departments.existsById(department))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Department does not exist");}
    private static String normalize(String s){return s.trim().toLowerCase(Locale.ROOT);} private static String blank(String s){return s==null||s.isBlank()?null:s.trim();}
    private static ResponseStatusException conflict(String s){return new ResponseStatusException(HttpStatus.CONFLICT,s);} private static ResponseStatusException forbidden(){return new ResponseStatusException(HttpStatus.FORBIDDEN,"Active Admin account required");}
}

@RestController @RequestMapping("/api") class UserController {
    final UserService service;final UserRepository users;final RoleRepository roles;final DepartmentRepository departments;
    UserController(UserService s,UserRepository u,RoleRepository r,DepartmentRepository d){service=s;users=u;roles=r;departments=d;}
    @PostMapping("/users") @ResponseStatus(HttpStatus.CREATED) UserResponse create(@RequestHeader("X-Admin-Id") String admin,@Valid @RequestBody CreateUserRequest r){service.requireAdmin(admin);return service.create(r);}
    @GetMapping("/users") List<UserResponse> all(@RequestHeader("X-Admin-Id") String admin){service.requireAdmin(admin);return users.findAll().stream().map(UserResponse::of).toList();}
    @GetMapping("/users/{id}") UserResponse one(@RequestHeader("X-Admin-Id") String admin,@PathVariable String id){service.requireAdmin(admin);return service.one(id);}
    @PutMapping("/users/{id}") UserResponse update(@RequestHeader("X-Admin-Id") String admin,@PathVariable String id,@Valid @RequestBody UpdateUserRequest r){service.requireAdmin(admin);return service.update(id,r);}
    @PostMapping("/users/{id}/lock") UserResponse lock(@RequestHeader("X-Admin-Id") String admin,@PathVariable String id){service.requireAdmin(admin);if(admin.equals(id))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Admin cannot lock own account");return service.lock(id,true);}
    @PostMapping("/users/{id}/unlock") UserResponse unlock(@RequestHeader("X-Admin-Id") String admin,@PathVariable String id){service.requireAdmin(admin);return service.lock(id,false);}
    @GetMapping("/roles") List<Role> roles(){return roles.findAll();}@GetMapping("/departments") List<Department> departments(){return departments.findAll();}
}
