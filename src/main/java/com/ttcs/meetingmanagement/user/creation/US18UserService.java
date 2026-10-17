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
        if(!roles.existsById(r.roleId()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Vai trò không tồn tại");
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
}
