package com.ttcs.meetingmanagement.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "department", uniqueConstraints = @UniqueConstraint(name = "uk_department_hrm_code", columnNames = "hrm_code"))
public class Department {
    @Id @Column(name = "department_id", length = 50)
    public String id;
    @Column(name = "department_name")
    public String name;
    @Column(name = "hrm_code")
    public String hrmCode;
    @Column(name = "last_synced_at")
    public LocalDateTime lastSyncedAt;

    public Department() {}
    public Department(String id, String name) { this.id = id; this.name = name; }
    @PrePersist void initialize() { if (id == null) id = UUID.randomUUID().toString(); }
    public String getId() { return id; }
    public String getName() { return name; }
    public String getHrmCode() { return hrmCode; }
    public LocalDateTime getLastSyncedAt() { return lastSyncedAt; }
}
