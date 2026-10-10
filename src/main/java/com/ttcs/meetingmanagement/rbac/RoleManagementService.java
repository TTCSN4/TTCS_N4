
package com.ttcs.meetingmanagement.rbac;

import com.ttcs.meetingmanagement.model.Role;
import com.ttcs.meetingmanagement.model.User;
import com.ttcs.meetingmanagement.repository.RoleRepository;
import com.ttcs.meetingmanagement.repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.access.prepost.PreAuthorize;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@PreAuthorize("@rbacAccess.isAdmin(authentication)")
public class RoleManagementService {

    private static final Set<String> ALLOWED_ROLES = Set.of(
            "Admin",
            "Người đặt lịch",
            "Người tham dự"
    );

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
            String roleName
    ) {
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

        Role targetRole = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Role not found"));

        if (!ALLOWED_ROLES.contains(targetRole.getRoleName())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported role");
        }

        Role currentRole = user.getRole();

        // Khong can cap nhat neu vai tro khong thay doi
        if (currentRole != null &&
                currentRole.getRoleId().equals(targetRole.getRoleId())) {
            return toResult(user);
        }

        // Khong cho phep xoa Admin cuoi cung
        if (isAdmin(currentRole) && !isAdmin(targetRole)) {

            roleRepository.lockById(currentRole.getRoleId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Admin role not found"));

            long adminCount = userRepository
                    .countByRole_RoleId(currentRole.getRoleId());

            if (adminCount <= 1) {
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

    // Thu hoi vai tro dac quyen
    // Chuyen nguoi dung ve vai tro Nguoi tham du
    @Transactional
    public RoleResult revokeRole(String userId) {

        Role participant = roleRepository
                .findByRoleName("Người tham dự")
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Default participant role is missing"));

        return assignRole(userId, participant.getRoleId());
    }

    private boolean isAdmin(Role role) {
        return role != null &&
                "Admin".equals(role.getRoleName());
    }

    private RoleResult toResult(User user) {

        Role role = user.getRole();

        return new RoleResult(
                user.getUserId(),
                role == null ? null : role.getRoleId(),
                role == null ? null : role.getRoleName()
        );
    }
}
