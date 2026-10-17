package com.ttcs.meetingmanagement.model;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "room_restriction")
public class RoomRestriction {

    @Id
    @Column(name = "restriction_id", length = 50)
    private String restrictionId;

    @Column(name = "room_id", nullable = false, length = 50)
    private String roomId;

    @Column(name = "user_id", length = 50)
    private String userId;

    @Column(name = "role_id", length = 50)
    private String roleId;

    @Column(name = "department_id", length = 50)
    private String departmentId;

    @Column(name = "notes", length = 255)
    private String notes;

    @PrePersist
    void initialize() {
        if (restrictionId == null || restrictionId.isBlank()) {
            restrictionId = UUID.randomUUID().toString();
        }
    }

    public RoomRestriction() {
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
