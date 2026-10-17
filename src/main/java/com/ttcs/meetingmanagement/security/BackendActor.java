package com.ttcs.meetingmanagement.security;

import com.ttcs.meetingmanagement.rbac.RbacAccess;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

public final class BackendActor {
    private BackendActor() {}

    public static String requireUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        String id = RbacAccess.extractUserId(authentication);
        if (id != null && !id.isBlank()) {
            return id;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user identity is required");
    }
}
