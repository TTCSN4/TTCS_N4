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

    // Admin duoc quan ly phong va vai tro nguoi dung
    public boolean isAdmin(Authentication authentication) {
        return hasRole(
                authentication,
                "Admin",
                "ROLE_ADMIN",
                "ADMIN"
        );
    }

    // Admin va Nguoi dat lich duoc tao lich hop
    public boolean canBook(Authentication authentication) {
        return hasRole(
                authentication,
                "Admin",
                "ROLE_ADMIN",
                "ADMIN",
                "Người đặt lịch",
                "BOOKER"
        );
    }

    // Cac vai tro duoc phep tham gia va xem phong hop
    public boolean canAttend(Authentication authentication) {
        return hasRole(
                authentication,
                "Admin",
                "ROLE_ADMIN",
                "ADMIN",
                "Người đặt lịch",
                "BOOKER",
                "Người tham dự",
                "ATTENDEE",
                "USER"
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
        // Do not trust a client-provided userId.
        String userId = null;
        Object principal = authentication.getPrincipal();

        if (principal instanceof AuthenticatedUserIdentity identity) {
            userId = identity.getUserId();
        } else if (principal instanceof Jwt jwt) {
            userId = jwt.getSubject();
        } else if (principal instanceof String s && !s.isBlank()) {
            userId = s;
        } else if (authentication.getName() != null && !authentication.getName().isBlank()) {
            userId = authentication.getName();
        }

        if (userId == null || userId.isBlank()) {
            return false;
        }

        // Read role from USER -> ROLE according to ERD
        return userRepository.findWithRole(userId)
                .map(User::getRole)
                .filter(role -> role != null)
                .map(role -> Arrays.stream(allowedRoles)
                        .anyMatch(name ->
                                name.equalsIgnoreCase(role.getRoleName())
                                || name.equalsIgnoreCase(role.getRoleId())))
                .orElse(false);
    }
}