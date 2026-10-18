
package com.ttcs.meetingmanagement.rbac;

import com.ttcs.meetingmanagement.model.User;
import com.ttcs.meetingmanagement.repository.UserRepository;

import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
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

    // Admin duoc quan ly vai tro nguoi dung
    public boolean isAdmin(Authentication authentication) {
        return hasRole(authentication, "Admin", "ADMIN", "ROLE_ADMIN");
    }

    // Admin va Nguoi dat lich duoc tao lich hop
    public boolean canBook(Authentication authentication) {
        return hasRole(
                authentication,
                "Admin", "ADMIN", "ROLE_ADMIN",
                "Người đặt lịch", "PM", "ROLE_PM", "PROJECT_MANAGER"
        );
    }

    // Cac vai tro duoc phep tham gia cuoc hop
    public boolean canAttend(Authentication authentication) {
        return hasRole(
                authentication,
                "Admin", "ADMIN", "ROLE_ADMIN",
                "Người đặt lịch", "PM", "ROLE_PM", "PROJECT_MANAGER",
                "Người tham dự", "ATTENDEE", "MEMBER"
        );
    }

    // US07: Chi ADMIN va MANAGER (PM) moi duoc su dung chuc nang danh sach phong trong
    public boolean canAccessAvailableRooms(Authentication authentication) {
        return hasRole(
                authentication,
                "ADMIN", "ROLE_ADMIN", "Admin",
                "PM", "ROLE_PM", "PROJECT_MANAGER", "Project Manager", "Manager"
        );
    }

    private boolean hasRole(
            Authentication authentication,
            String... allowedRoles) {

        // Reject unauthenticated requests
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return false;
        }

        // Identity must come from trusted authentication.
        // Do not trust a client-provided userId or role.
        final String userId = extractUserId(authentication);

        if (userId == null || userId.isBlank()) {
            return false;
        }

        // Read role from USER -> ROLE according to ERD from database
        return userRepository.findWithRole(userId)
                .or(() -> userRepository.findByEmailIgnoreCase(userId).flatMap(u -> userRepository.findWithRole(u.getUserId())))
                .map(User::getRole)
                .filter(java.util.Objects::nonNull)
                .map(role -> Arrays.stream(allowedRoles)
                        .anyMatch(name ->
                                name.equalsIgnoreCase(role.getRoleName()) ||
                                name.equalsIgnoreCase(role.getRoleId())))
                .orElse(false);
    }

    private String extractUserId(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof AuthenticatedUserIdentity identity) {
            return identity.getUserId();
        }
        if (principal instanceof org.springframework.security.oauth2.jwt.Jwt jwt) {
            return jwt.getSubject();
        }
        if (principal instanceof org.springframework.security.core.userdetails.UserDetails userDetails) {
            return userDetails.getUsername();
        }
        if (authentication.getName() != null && !authentication.getName().isBlank()) {
            return authentication.getName();
        }
        return null;
    }
}
