package com.ttcs.meetingmanagement.model;

import jakarta.persistence.*;
import java.util.UUID;

/** ERD restriction plus the role/department scope already used by develop's US21 SQL. */
@Entity
@Table(name = "room_restriction")
public class RoomRestriction {

    @Id
    @Column(name = "restriction_id", nullable = false, length = 50)
    public String restrictionId;

    @Column(name = "room_id", nullable = false, length = 50)
    public String roomId;

    @Column(name = "user_id", length = 50)
    public String userId;

    @Column(name = "role_id", length = 50)
    public String roleId;

    @Column(name = "department_id", length = 50)
    public String departmentId;

    @Column(name = "notes", columnDefinition = "TEXT")
    public String notes;

    public RoomRestriction() {
    }

    public RoomRestriction(String restrictionId, String roomId, String userId, String notes) {
        this.restrictionId = restrictionId;
        this.roomId = roomId;
        this.userId = userId;
        this.notes = notes;
    }

    @PrePersist
    void initialize() {
        if (restrictionId == null) {
            restrictionId = UUID.randomUUID().toString();
        }
    }

    public String getRestrictionId() {
        return restrictionId;
    }

    public void setRestrictionId(String restrictionId) {
        this.restrictionId = restrictionId;
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getRoleId() {
        return roleId;
    }

    public void setRoleId(String roleId) {
        this.roleId = roleId;
    }

    public String getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(String departmentId) {
        this.departmentId = departmentId;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
