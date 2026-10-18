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
@RestController @RequestMapping("/api") class US18UserController {
    final US18UserService service;
    final UserRepository users;
    final RoleRepository roles;
    final DepartmentRepository departments;
    US18UserController(US18UserService s,UserRepository u,RoleRepository r,DepartmentRepository d) {
        service=s;
        users=u;
        roles=r;
        departments=d;
    }
    @PostMapping("/users") @ResponseStatus(HttpStatus.CREATED) US18UserResponse create(@Valid @RequestBody US18CreateUserRequest r) {
        return service.create(r);
    }
    @GetMapping("/users") List<US18UserResponse> all() {
        return users.findAll().stream().map(US18UserResponse::of).toList();
    }
    @GetMapping("/users/{id}") US18UserResponse get(@PathVariable String id){return service.get(id);}
    @PutMapping("/users/{id}") US18UserResponse update(@PathVariable String id,@Valid @RequestBody US18UpdateUserRequest r){return service.update(id,r);}
    @PostMapping("/users/{id}/lock") US18UserResponse lock(@PathVariable String id){return service.setLocked(id,true,com.ttcs.meetingmanagement.security.BackendActor.requireUserId());}
    @PostMapping("/users/{id}/unlock") US18UserResponse unlock(@PathVariable String id){return service.setLocked(id,false,com.ttcs.meetingmanagement.security.BackendActor.requireUserId());}
    @GetMapping("/roles") List<Role> roles() {
        return roles.findAll();
    }
    @GetMapping("/departments") List<Department> departments() {
        return departments.findAll();
    }
}
