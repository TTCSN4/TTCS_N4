package com.ttcs.meetingmanagement.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "department")
public class Department {

    @Id
    @Column(name = "department_id", nullable = false, length = 50)
    private String departmentId;

    @Column(name = "department_name", nullable = false, length = 255)
    private String departmentName;

    @Column(name = "hrm_code", length = 50)
    private String hrmCode;

    @Column(name = "last_synced_at")
    private OffsetDateTime lastSyncedAt;

    public Department() {
    }

    public Department(String departmentId, String departmentName, String hrmCode, OffsetDateTime lastSyncedAt) {
        this.departmentId = departmentId;
        this.departmentName = departmentName;
        this.hrmCode = hrmCode;
        this.lastSyncedAt = lastSyncedAt;
    }

    public String getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(String departmentId) {
        this.departmentId = departmentId;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }

    public String getHrmCode() {
        return hrmCode;
    }

    public void setHrmCode(String hrmCode) {
        this.hrmCode = hrmCode;
    }

    public OffsetDateTime getLastSyncedAt() {
        return lastSyncedAt;
    }

    public void setLastSyncedAt(OffsetDateTime lastSyncedAt) {
        this.lastSyncedAt = lastSyncedAt;
    }
}
