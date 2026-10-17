package com.ttcs.meetingmanagement.rbac;

import com.ttcs.meetingmanagement.model.Role;
import com.ttcs.meetingmanagement.model.User;
import com.ttcs.meetingmanagement.repository.MeetingRepository;
import com.ttcs.meetingmanagement.repository.UserRepository;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.util.Arrays;
import java.util.Optional;

@Component("rbacAccess")
@Transactional(readOnly = true)
public class RbacAccess {

    private final UserRepository userRepository;
    private final MeetingRepository meetingRepository;

    public RbacAccess(
            UserRepository userRepository,
            MeetingRepository meetingRepository) {
        this.userRepository = userRepository;
        this.meetingRepository = meetingRepository;
    }

    // ==========================================
    // 1. EXTRACT USER ID FROM ANY PRINCIPAL
    // ==========================================
    public static String extractUserId(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof AuthenticatedUserIdentity identity) {
            String id = identity.getUserId();
            if (id != null && !id.isBlank()) {
                return id.trim();
            }
        }

        if (principal instanceof Jwt jwt) {
            String sub = jwt.getSubject();
            if (sub != null && !sub.isBlank()) {
                return sub.trim();
            }
            String uidClaim = jwt.getClaimAsString("userId");
            if (uidClaim != null && !uidClaim.isBlank()) {
                return uidClaim.trim();
            }
        }

        if (principal instanceof UserDetails userDetails) {
            String username = userDetails.getUsername();
            if (username != null && !username.isBlank()) {
                return username.trim();
            }
        }

        if (principal instanceof Principal p) {
            String name = p.getName();
            if (name != null && !name.isBlank()) {
                return name.trim();
            }
        }

        if (principal instanceof String str && !str.isBlank()) {
            return str.trim();
        }

        String authName = authentication.getName();
        if (authName != null && !authName.isBlank()) {
            return authName.trim();
        }

        return null;
    }

    // ==========================================
    // 2. CHECK ROLE CATEGORY (ALWAYS FRESH FROM DB)
    // ==========================================
    public boolean hasRoleCategory(
            Authentication authentication,
            RoleCategory... allowedCategories) {

        String userId = extractUserId(authentication);
        if (userId == null || userId.isBlank()) {
            return false;
        }

        return hasRoleCategory(userId, allowedCategories);
    }

    public boolean hasRoleCategory(
            String userId,
            RoleCategory... allowedCategories) {

        if (userId == null || userId.isBlank()) {
            return false;
        }

        Optional<User> userOpt = userRepository.findWithRole(userId.trim());
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findByEmailIgnoreCase(userId.trim())
                    .flatMap(u -> userRepository.findWithRole(u.getUserId()));
        }

        if (userOpt.isEmpty()) {
            return false;
        }

        User user = userOpt.get();

        // Account must be ACTIVE (reject LOCKED or inactive accounts)
        String status = user.getStatus();
        if (status == null || !"ACTIVE".equalsIgnoreCase(status.trim())) {
            return false;
        }

        Role role = user.getRole();
        if (role == null) {
            return false;
        }

        RoleCategory actualCategory = RoleCategory.categorize(role);
        if (actualCategory == null) {
            return false;
        }

        return Arrays.stream(allowedCategories)
                .anyMatch(c -> c == actualCategory);
    }

    // ==========================================
    // 3. ROLE CONVENIENCE METHODS
    // ==========================================
    public boolean isAdmin(Authentication authentication) {
        return hasRoleCategory(authentication, RoleCategory.ADMIN);
    }

    public boolean isAdmin(String userId) {
        return hasRoleCategory(userId, RoleCategory.ADMIN);
    }

    public boolean isPm(Authentication authentication) {
        return hasRoleCategory(authentication, RoleCategory.PM);
    }

    public boolean isPm(String userId) {
        return hasRoleCategory(userId, RoleCategory.PM);
    }

    public boolean isMember(Authentication authentication) {
        return hasRoleCategory(authentication, RoleCategory.MEMBER);
    }

    public boolean isMember(String userId) {
        return hasRoleCategory(userId, RoleCategory.MEMBER);
    }

    // Admin hoac PM co quyen tao / dat phong
    public boolean canBook(Authentication authentication) {
        return hasRoleCategory(
                authentication,
                RoleCategory.ADMIN,
                RoleCategory.PM
        );
    }

    public boolean canBook(String userId) {
        return hasRoleCategory(
                userId,
                RoleCategory.ADMIN,
                RoleCategory.PM
        );
    }

    // Tat ca cac vai tro hop le trong he thong
    public boolean canAttend(Authentication authentication) {
        return hasRoleCategory(
                authentication,
                RoleCategory.ADMIN,
                RoleCategory.PM,
                RoleCategory.MEMBER
        );
    }

    public boolean canAttend(String userId) {
        return hasRoleCategory(
                userId,
                RoleCategory.ADMIN,
                RoleCategory.PM,
                RoleCategory.MEMBER
        );
    }

    // ==========================================
    // 4. CHECK MEETING OWNERSHIP / MANAGEMENT
    // ==========================================
    public boolean canManageMeeting(
            Authentication authentication,
            String meetingId) {

        if (isAdmin(authentication)) {
            return true;
        }

        String userId = extractUserId(authentication);
        if (userId == null || userId.isBlank() || meetingId == null || meetingId.isBlank()) {
            return false;
        }

        if (!canBook(authentication)) {
            return false;
        }

        return meetingRepository.findById(meetingId)
                .map(m -> userId.equals(m.getOrganizerId()))
                .orElse(false);
    }
}
