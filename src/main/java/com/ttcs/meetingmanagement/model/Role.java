
package com.ttcs.meetingmanagement.model;

import jakarta.persistence.*;

@Entity
@Table(name = "`role`")
public class Role {

    @Id
    @Column(name = "role_id")
    public String roleId;

    @Column(name = "role_name")
    public String roleName;

    public Role() {
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
