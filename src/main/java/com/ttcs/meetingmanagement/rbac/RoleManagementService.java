package com.ttcs.meetingmanagement.rbac;

import com.ttcs.meetingmanagement.model.Role;
import com.ttcs.meetingmanagement.model.User;
import com.ttcs.meetingmanagement.repository.RoleRepository;
import com.ttcs.meetingmanagement.repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
@PreAuthorize("@rbacAccess.isAdmin(authentication)")
public class RoleManagementService {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;

    public RoleManagementService(
            RoleRepository roleRepository,
            UserRepository userRepository) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
    }

    public record RoleResult(
            String userId,
            String roleId,
            String roleName,
            String roleCategory
    ) {
        public RoleResult(String userId, String roleId, String roleName) {
            this(userId, roleId, roleName,
                    roleName != null && RoleCategory.categorize(roleId, roleName) != null
                            ? RoleCategory.categorize(roleId, roleName).name()
                            : null);
        }
    }

    // Lay danh sach vai tro
    @Transactional(readOnly = true)
    public List<Role> getAllRoles() {
        return roleRepository.findAll();
    }

    // Lay vai tro cua mot nguoi dung
    @Transactional(readOnly = true)
    public RoleResult getUserRole(String userId) {
        User user = userRepository.findWithRole(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found"));

        return toResult(user);
    }

    // Gan hoac thay doi vai tro
    @Transactional
    public RoleResult assignRole(
            String userId,
            String roleId) {

        if (roleId == null || roleId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Role ID is required");
        }

        User user = userRepository.lockByIdWithRole(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found"));

        Role targetRole = roleRepository.findById(roleId.trim())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Role not found"));

        RoleCategory targetCategory = RoleCategory.categorize(targetRole);
        if (targetCategory == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported role");
        }

        Role currentRole = user.getRole();
        RoleCategory currentCategory = RoleCategory.categorize(currentRole);

        // Khong can cap nhat neu vai tro khong thay doi
        if (currentRole != null && currentRole.getRoleId().equals(targetRole.getRoleId())) {
            return toResult(user);
        }

        // Khong cho phep ha quyen Admin cuoi cung
        if (currentCategory == RoleCategory.ADMIN && targetCategory != RoleCategory.ADMIN) {
            long activeAdminCount = userRepository.findAll().stream()
                    .filter(u -> "ACTIVE".equalsIgnoreCase(u.getStatus()))
                    .filter(u -> RoleCategory.categorize(u.getRole()) == RoleCategory.ADMIN)
                    .count();

            if (activeAdminCount <= 1) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Cannot remove the last administrator");
            }
        }

        // USER.role_id -> ROLE.role_id theo ERD
        user.setRole(targetRole);
        user.setUpdatedAt(LocalDateTime.now());

        userRepository.saveAndFlush(user);

        return toResult(user);
    }

    // Thu hoi vai tro dac quyen - chuyen nguoi dung ve vai tro Member / Nguoi tham du
    @Transactional
    public RoleResult revokeRole(String userId) {
        Role participant = roleRepository.findAll().stream()
                .filter(r -> RoleCategory.categorize(r) == RoleCategory.MEMBER)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Default participant role is missing"));

        return assignRole(userId, participant.getRoleId());
    }

    // Cap nhat trang thai nguoi dung (ACTIVE, LOCKED, INACTIVE, ...)
    @Transactional
    public RoleResult updateUserStatus(String userId, String status) {
        if (status == null || status.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Trang thai khong duoc de trong");
        }

        User user = userRepository.lockByIdWithRole(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Nguoi dung khong ton tai"));

        String normalizedStatus = status.trim().toUpperCase(java.util.Locale.ROOT);
        Role currentRole = user.getRole();
        RoleCategory currentCategory = RoleCategory.categorize(currentRole);

        // Khong cho phep khoa hoac vo hieu hoa Admin cuoi cung
        if (currentCategory == RoleCategory.ADMIN && !"ACTIVE".equalsIgnoreCase(normalizedStatus)) {
            long activeAdminCount = userRepository.findAll().stream()
                    .filter(u -> "ACTIVE".equalsIgnoreCase(u.getStatus()))
                    .filter(u -> RoleCategory.categorize(u.getRole()) == RoleCategory.ADMIN)
                    .count();

            if (activeAdminCount <= 1) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Khong the khoa hoac vo hieu hoa quan tri vien cuoi cung");
            }
        }

        user.setStatus(normalizedStatus);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.saveAndFlush(user);

        return toResult(user);
    }

    public boolean isAdmin(Role role) {
        return RoleCategory.categorize(role) == RoleCategory.ADMIN;
    }

    private RoleResult toResult(User user) {
        Role role = user.getRole();
        RoleCategory category = RoleCategory.categorize(role);
        return new RoleResult(
                user.getUserId(),
                role == null ? null : role.getRoleId(),
                role == null ? null : role.getRoleName(),
                category == null ? null : category.name()
        );
    }
}
