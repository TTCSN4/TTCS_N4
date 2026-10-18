package com.ttcs.meetingmanagement.user.creation;
import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.boot.*;
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
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.ttcs.meetingmanagement.model.*;
import com.ttcs.meetingmanagement.repository.*;
@Service class US18UserService {
    final UserRepository users;
    final RoleRepository roles;
    final DepartmentRepository departments;
    final BCryptPasswordEncoder encoder;
    US18UserService(UserRepository u,RoleRepository r,DepartmentRepository d,BCryptPasswordEncoder e) {
        users=u;
        roles=r;
        departments=d;
        encoder=e;
    }
    @Transactional US18UserResponse create(US18CreateUserRequest r) {
        String email=r.email().trim().toLowerCase(Locale.ROOT);
        if(users.existsByEmailIgnoreCase(email))throw new ResponseStatusException(HttpStatus.CONFLICT,"Email đã tồn tại");
        validateRole(r.roleId());
        if(r.departmentId()!=null&&!r.departmentId().isBlank()&&!departments.existsById(r.departmentId()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Phòng ban không tồn tại");
        if(r.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Password must be at most 72 UTF-8 bytes");
        User u=new User();
        u.fullName=r.fullName().trim();
        u.email=email;
        u.phone=r.phone();
        u.passwordHash=encoder.encode(r.password());
        u.role=roles.getReferenceById(r.roleId());
        u.departmentId=(r.departmentId()==null||r.departmentId().isBlank())?null:r.departmentId();
        return US18UserResponse.of(users.save(u));
    }

    @Transactional US18UserResponse update(String id,US18UpdateUserRequest r){
        User u=locked(id);String email=r.email().trim().toLowerCase(Locale.ROOT);
        users.findByEmailIgnoreCase(email).filter(other->!other.userId.equals(id)).ifPresent(other->{throw error(HttpStatus.CONFLICT,"Email already exists");});
        validateRole(r.roleId());
        if(r.departmentId()!=null&&!r.departmentId().isBlank()&&!departments.existsById(r.departmentId()))throw error(HttpStatus.BAD_REQUEST,"Department does not exist");
        var role=roles.findById(r.roleId()).orElseThrow();
        protectLastAdmin(u,role.getRoleName(),u.status);
        u.fullName=r.fullName().trim();u.email=email;u.phone=r.phone();u.role=role;u.departmentId=r.departmentId()==null||r.departmentId().isBlank()?null:r.departmentId();
        return US18UserResponse.of(users.saveAndFlush(u));
    }
    @Transactional US18UserResponse setLocked(String id,boolean lock,String actor){
        if(lock&&id.equals(actor))throw error(HttpStatus.BAD_REQUEST,"Admin cannot lock their own account");
        User u=locked(id);String status=lock?"LOCKED":"ACTIVE";
        protectLastAdmin(u,u.role==null?null:u.role.getRoleName(),status);u.status=status;
        return US18UserResponse.of(users.saveAndFlush(u));
    }
    @Transactional(readOnly=true) US18UserResponse get(String id){return US18UserResponse.of(users.findWithRole(id).orElseThrow(()->error(HttpStatus.NOT_FOUND,"User not found")));}
    private User locked(String id){return users.lockByIdWithRole(id).orElseThrow(()->error(HttpStatus.NOT_FOUND,"User not found"));}
    private void validateRole(String id){
        var role=roles.findById(id).orElseThrow(()->error(HttpStatus.BAD_REQUEST,"Role does not exist"));
        if(!Set.of("ADMIN","PM","MEMBER","NGƯỜI ĐẶT LỊCH","NGƯỜI THAM DỰ").contains(role.getRoleName().toUpperCase(Locale.ROOT)))throw error(HttpStatus.BAD_REQUEST,"Role must be Admin, PM or Member");
    }
    private void protectLastAdmin(User u,String roleName,String status){
        if(u.role!=null&&"Admin".equalsIgnoreCase(u.role.getRoleName())&&"ACTIVE".equalsIgnoreCase(u.status)
            &&(!"Admin".equalsIgnoreCase(roleName)||!"ACTIVE".equalsIgnoreCase(status))){
            roles.lockById(u.role.getRoleId()).orElseThrow();
            long active=users.findAll().stream().filter(x->x.role!=null&&"Admin".equalsIgnoreCase(x.role.getRoleName())&&"ACTIVE".equalsIgnoreCase(x.status)).count();
            if(active<=1)throw error(HttpStatus.CONFLICT,"Cannot disable the last active Admin");
        }
    }
    private static ResponseStatusException error(HttpStatus status,String message){return new ResponseStatusException(status,message);}
}
