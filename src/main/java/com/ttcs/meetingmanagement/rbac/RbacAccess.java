
package com.ttcs.meetingmanagement.rbac;

import com.ttcs.meetingmanagement.model.User;
import com.ttcs.meetingmanagement.repository.UserRepository;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;

@Component("rbacAccess")
@Transactional(readOnly = true)
public class RbacAccess {

    private final UserRepository userRepository;

    public RbacAccess(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public boolean isAdmin(Authentication authentication) {
        return hasRole(authentication, "Admin");
    }

    public boolean canBook(Authentication authentication) {
        return hasRole(authentication, "Admin", "Người đặt lịch");
    }

    public boolean canAttend(Authentication authentication) {
        return hasRole(authentication, "Admin", "Người đặt lịch", "Người tham dự");
    }

    private boolean hasRole(Authentication authentication, String... allowedRoles) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return false;
        }

        String userId = trustedUserId(authentication.getPrincipal());
        if (userId == null || userId.isBlank()) {
            return false;
        }

        // Role and account state are always taken from the database, never JWT claims.
        return userRepository.findWithRole(userId)
                .filter(user -> "ACTIVE".equalsIgnoreCase(user.getStatus()))
                .map(User::getRole)
                .filter(role -> role != null)
                .map(role -> Arrays.stream(allowedRoles)
                        .anyMatch(name -> name.equals(role.getRoleName())))
                .orElse(false);
    }

    private String trustedUserId(Object principal) {
        if (principal instanceof Jwt jwt) {
            return jwt.getSubject();
        }
        if (principal instanceof AuthenticatedUserIdentity identity) {
            return identity.getUserId();
        }
        return null;
    }
}
