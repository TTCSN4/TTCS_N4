package com.ttcs.meetingmanagement.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "department", uniqueConstraints = @UniqueConstraint(name = "uk_department_hrm_code", columnNames = "hrm_code"))
public class Department {

    @Id
    @Column(name = "department_id", length = 50)
    public String id;

    @Column(name = "department_name")
    public String name;

    @Column(name = "hrm_code")
    public String hrmCode;

    @Column(name = "last_synced_at")
    public LocalDateTime lastSyncedAt;

    public Department() {}

    public Department(String id, String name) {
        this.id = id;
        this.name = name;
    }

    @PrePersist
    void initialize() {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDepartmentId() { return id; }
    public void setDepartmentId(String departmentId) { this.id = departmentId; }

    public String getDepartmentName() { return name; }
    public void setDepartmentName(String departmentName) { this.name = departmentName; }

    public String getHrmCode() { return hrmCode; }
    public void setHrmCode(String hrmCode) { this.hrmCode = hrmCode; }

    public LocalDateTime getLastSyncedAt() { return lastSyncedAt; }
    public void setLastSyncedAt(LocalDateTime lastSyncedAt) { this.lastSyncedAt = lastSyncedAt; }
}
