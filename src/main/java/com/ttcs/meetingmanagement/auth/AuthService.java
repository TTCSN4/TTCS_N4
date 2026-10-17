package com.ttcs.meetingmanagement.auth;

import com.ttcs.meetingmanagement.auth.LoginResponse.UserSummary;
import com.ttcs.meetingmanagement.model.Role;
import com.ttcs.meetingmanagement.model.User;
import com.ttcs.meetingmanagement.rbac.RoleCategory;
import com.ttcs.meetingmanagement.repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public AuthService(
            UserRepository userRepository,
            BCryptPasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        if (request == null || request.getIdentifier().isBlank() || request.password() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }

        String identifier = request.getIdentifier();

        // 1. Lookup user by email, userId, or hrmCode
        Optional<User> userOpt = userRepository.findByEmailIgnoreCase(identifier);
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findWithRole(identifier);
        }
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findByHrmCode(identifier);
        }

        if (userOpt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }

        User user = userOpt.get();

        // Ensure role is loaded
        if (user.getRole() == null && user.getUserId() != null) {
            user = userRepository.findWithRole(user.getUserId()).orElse(user);
        }

        // 2. Validate password
        String storedHash = user.getPasswordHash();
        if (storedHash == null || storedHash.isBlank() || !passwordEncoder.matches(request.password(), storedHash)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }

        // 3. Check account status
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is locked or inactive");
        }

        // 4. Check assigned role
        Role role = user.getRole();
        if (role == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account has no assigned role");
        }

        RoleCategory category = RoleCategory.categorize(role);
        if (category == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account has an invalid role configuration");
        }

        // 5. Generate token
        String token = jwtTokenService.generateToken(user);

        UserSummary summary = new UserSummary(
                user.getUserId(),
                user.getEmail(),
                user.getFullName(),
                role.getRoleId(),
                role.getRoleName(),
                category.name(),
                user.getStatus()
        );

        return new LoginResponse(token, "Bearer", jwtTokenService.getExpirationSeconds(), summary);
    }

    @Transactional(readOnly = true)
    public UserSummary getCurrentUser(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        User user = userRepository.findWithRole(userId.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is locked or inactive");
        }

        Role role = user.getRole();
        RoleCategory category = RoleCategory.categorize(role);

        return new UserSummary(
                user.getUserId(),
                user.getEmail(),
                user.getFullName(),
                role != null ? role.getRoleId() : null,
                role != null ? role.getRoleName() : null,
                category != null ? category.name() : null,
                user.getStatus()
        );
    }
}
