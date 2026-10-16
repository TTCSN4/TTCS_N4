package com.ttcs.meetingmanagement.security;

import com.ttcs.meetingmanagement.rbac.AuthenticatedUserIdentity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

public final class BackendActor {
    private BackendActor() {}
    public static String requireUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            Object principal = authentication.getPrincipal();
            String id = principal instanceof Jwt jwt ? jwt.getSubject()
                : principal instanceof AuthenticatedUserIdentity identity ? identity.getUserId() : null;
            if (id != null && !id.isBlank()) return id;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user identity is required");
    }
}
