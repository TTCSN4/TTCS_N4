package com.ttcs.meetingmanagement.auth;

import com.ttcs.meetingmanagement.auth.LoginResponse.UserSummary;
import com.ttcs.meetingmanagement.security.BackendActor;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping({"/api/auth/login", "/api/login"})
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping({"/api/auth/me", "/api/me"})
    public ResponseEntity<UserSummary> me() {
        String userId = BackendActor.requireUserId();
        UserSummary summary = authService.getCurrentUser(userId);
        return ResponseEntity.ok(summary);
    }
}
