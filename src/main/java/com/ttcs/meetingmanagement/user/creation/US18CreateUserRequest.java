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
record US18CreateUserRequest(@NotBlank String fullName,@Email @NotBlank String email,@Pattern(regexp="^$|^[0-9+() .-]{7,20}$") String phone,@NotBlank @Size(min=8,max=72) String password,@NotBlank String roleId,String departmentId) {
}
