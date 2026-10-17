package com.ttcs.meetingmanagement.model;

import jakarta.persistence.*;

@Entity
@Table(name = "`role`")
public class Role {

    @Id
    @Column(name = "role_id", length = 50)
    private String roleId;

    @Column(name = "role_name", nullable = false, length = 100)
    private String roleName;

    public Role() {
    }

    public Role(String roleId, String roleName) {
        this.roleId = roleId;
        this.roleName = roleName;
    }

    public String getRoleId() {
        return roleId;
    }

    public void setRoleId(String roleId) {
        this.roleId = roleId;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }
}
