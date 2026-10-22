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
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)) {
            Object principal = authentication.getPrincipal();
            String id = principal instanceof Jwt jwt ? jwt.getSubject()
                : principal instanceof AuthenticatedUserIdentity identity ? identity.getUserId()
                : principal instanceof org.springframework.security.core.userdetails.UserDetails userDetails ? userDetails.getUsername()
                : (principal instanceof String s && !"anonymousUser".equalsIgnoreCase(s)) ? s
                : (authentication.getName() != null && !"anonymousUser".equalsIgnoreCase(authentication.getName())) ? authentication.getName()
                : null;
            if (id != null && !id.isBlank()) return id.trim();
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user identity is required");
    }
}
