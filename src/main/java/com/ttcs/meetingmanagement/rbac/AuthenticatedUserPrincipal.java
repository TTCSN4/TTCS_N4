package com.ttcs.meetingmanagement.rbac;

import java.security.Principal;

public class AuthenticatedUserPrincipal implements AuthenticatedUserIdentity, Principal {

    private final String userId;
    private final String email;
    private final String fullName;
    private final String roleId;
    private final String roleName;
    private final RoleCategory roleCategory;

    public AuthenticatedUserPrincipal(
            String userId,
            String email,
            String fullName,
            String roleId,
            String roleName,
            RoleCategory roleCategory) {
        this.userId = userId;
        this.email = email;
        this.fullName = fullName;
        this.roleId = roleId;
        this.roleName = roleName;
        this.roleCategory = roleCategory;
    }

    @Override
    public String getUserId() {
        return userId;
    }

    @Override
    public String getName() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public String getFullName() {
        return fullName;
    }

    public String getRoleId() {
        return roleId;
    }

    public String getRoleName() {
        return roleName;
    }

    public RoleCategory getRoleCategory() {
        return roleCategory;
    }
}
