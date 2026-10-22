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
import java.time.LocalDateTime;
import java.util.*;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.ttcs.meetingmanagement.model.*;
import com.ttcs.meetingmanagement.repository.*;
record US18UserResponse(String id,String fullName,String email,String phone,String status,String roleId,String departmentId,LocalDateTime createdAt) {
    static US18UserResponse of(User u) {
        return new US18UserResponse(u.userId,u.fullName,u.email,u.phone,u.status,u.getRoleId(),u.departmentId,u.createdAt);
    }
}
