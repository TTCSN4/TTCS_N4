package com.ttcs.meetingmanagement.user.creation;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import com.ttcs.meetingmanagement.model.*;
import com.ttcs.meetingmanagement.repository.*;

@RestController
@RequestMapping("/api")
class US18UserController {

    final US18UserService service;
    final UserRepository users;
    final RoleRepository roles;
    final DepartmentRepository departments;

    US18UserController(US18UserService s, UserRepository u, RoleRepository r, DepartmentRepository d) {
        service = s;
        users = u;
        roles = r;
        departments = d;
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@rbacAccess.isAdmin(authentication)")
    US18UserResponse create(@Valid @RequestBody US18CreateUserRequest r) {
        return service.create(r);
    }

    @GetMapping("/users")
    @PreAuthorize("@rbacAccess.canBook(authentication)")
    List<US18UserResponse> all() {
        return users.findAll().stream().map(US18UserResponse::of).toList();
    }

    @GetMapping("/roles")
    @PreAuthorize("@rbacAccess.canAttend(authentication)")
    List<Role> roles() {
        return roles.findAll();
    }

    @GetMapping("/departments")
    @PreAuthorize("@rbacAccess.canAttend(authentication)")
    List<Department> departments() {
        return departments.findAll();
    }
}
