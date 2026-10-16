
package com.ttcs.meetingmanagement.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "`user`", uniqueConstraints = @UniqueConstraint(name = "uk_user_email", columnNames = "email"))
public class User {

    @Id
    @Column(name = "user_id")
    public String userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id",
                referencedColumnName = "role_id")
    public Role role;

    @Column(name = "department_id")
    public String departmentId;

    @Column(name = "hrm_code", unique = true)
    public String hrmCode;

    @Column(name = "full_name")
    public String fullName;

    @Column(name = "email")
    public String email;

    @Column(name = "phone")
    public String phone;

    @Column(name = "status")
    public String status;

    @Column(name = "created_at")
    public LocalDateTime createdAt;

    @Column(name = "updated_at")
    public LocalDateTime updatedAt;

    @Column(name = "last_synced_at")
    public LocalDateTime lastSyncedAt;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(name = "password_hash")
    public String passwordHash;

    @PrePersist
    void initialize() {
        if (userId == null) userId = java.util.UUID.randomUUID().toString();
        if (createdAt == null) createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = "ACTIVE";
    }

    @PreUpdate
    void updateTimestamp() { updatedAt = LocalDateTime.now(); }

    public String getFullName() { return fullName; }
    public String getDepartmentId() { return departmentId; }
    public String getHrmCode() { return hrmCode; }
    public String getPhone() { return phone; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public LocalDateTime getLastSyncedAt() { return lastSyncedAt; }
    public void setUserId(String value) { userId = value; }
    public void setFullName(String value) { fullName = value; }
    public void setEmail(String value) { email = value; }
    public void setStatus(String value) { status = value; }
    public void setPhone(String value) { phone = value; }
    public void setDepartmentId(String value) { departmentId = value; }
    public void setHrmCode(String value) { hrmCode = value; }
    public void setLastSyncedAt(LocalDateTime value) { lastSyncedAt = value; }
    public String getRoleId() { return role == null ? null : role.getRoleId(); }

    public User() {
    }

    public String getUserId() {
        return userId;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public String getStatus() {
        return status;
    }

    public String getEmail() {
        return email;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
