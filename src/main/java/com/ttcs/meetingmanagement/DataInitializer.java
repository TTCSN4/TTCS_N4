package com.ttcs.meetingmanagement;

import com.ttcs.meetingmanagement.model.Role;
import com.ttcs.meetingmanagement.model.User;
import com.ttcs.meetingmanagement.repository.RoleRepository;
import com.ttcs.meetingmanagement.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;

    public DataInitializer(RoleRepository roleRepository, UserRepository userRepository) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
    }

    @Override
    public void run(String... args) {
        // Khởi tạo các vai trò chuẩn theo ERD
        Role adminRole = roleRepository.findById("ROLE_ADMIN").orElseGet(() -> {
            Role role = new Role("ROLE_ADMIN", "ADMIN");
            return roleRepository.save(role);
        });

        Role pmRole = roleRepository.findById("ROLE_PM").orElseGet(() -> {
            Role role = new Role("ROLE_PM", "PROJECT_MANAGER");
            return roleRepository.save(role);
        });

        // Khởi tạo tài khoản quản trị mẫu nếu chưa có
        if (!userRepository.existsById("USER001")) {
            User adminUser = new User();
            adminUser.setUserId("USER001");
            adminUser.setFullName("Nguyen Van A");
            adminUser.setEmail("user001@test.com");
            adminUser.setStatus("ACTIVE");
            adminUser.setRole(adminRole);
            adminUser.setCreatedAt(LocalDateTime.now());
            adminUser.setUpdatedAt(LocalDateTime.now());
            userRepository.save(adminUser);
        }
    }
}
