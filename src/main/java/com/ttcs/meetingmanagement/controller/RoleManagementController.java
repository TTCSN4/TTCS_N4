
package com.ttcs.meetingmanagement.controller;

import com.ttcs.meetingmanagement.model.Role;
import com.ttcs.meetingmanagement.rbac.RoleManagementService;
import com.ttcs.meetingmanagement.rbac.RoleManagementService.RoleResult;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("@rbacAccess.isAdmin(authentication)")
public class RoleManagementController {

    private final RoleManagementService roleService;

    public RoleManagementController(
            RoleManagementService roleService) {
        this.roleService = roleService;
    }

    public record AssignRoleRequest(
            @NotBlank String roleId
    ) {
    }

    @GetMapping("/roles")
    public List<Role> getRoles() {
        return roleService.getAllRoles();
    }

    @GetMapping("/users/{userId}/role")
    public RoleResult getUserRole(
            @PathVariable String userId) {
        return roleService.getUserRole(userId);
    }

    @PutMapping("/users/{userId}/role")
    public RoleResult assignRole(
            @PathVariable String userId,
            @Valid @RequestBody AssignRoleRequest request) {

        return roleService.assignRole(
                userId, request.roleId());
    }

    @DeleteMapping("/users/{userId}/role")
    public RoleResult revokeRole(
            @PathVariable String userId) {

        return roleService.revokeRole(userId);
    }
}
