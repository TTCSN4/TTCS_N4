
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
        return hasRole(authentication, "Admin");
    }

    // Admin va Nguoi dat lich duoc tao lich hop
    public boolean canBook(Authentication authentication) {
        return hasRole(
                authentication,
                "Admin",
                "Người đặt lịch"
        );
    }

    // Cac vai tro duoc phep tham gia cuoc hop
    public boolean canAttend(Authentication authentication) {
        return hasRole(
                authentication,
                "Admin",
                "Người đặt lịch",
                "Người tham dự"
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
        if (!(authentication.getPrincipal()
                instanceof AuthenticatedUserIdentity identity)) {
            return false;
        }

        String userId = identity.getUserId();

        if (userId == null || userId.isBlank()) {
            return false;
        }

        // Read role from USER -> ROLE according to ERD
        return userRepository.findWithRole(userId)
                .map(User::getRole)
                .filter(role -> role != null)
                .map(role -> Arrays.stream(allowedRoles)
                        .anyMatch(name ->
                                name.equals(role.getRoleName())))
                .orElse(false);
    }
}
