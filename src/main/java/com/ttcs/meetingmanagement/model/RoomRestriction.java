package com.ttcs.meetingmanagement.model;

import jakarta.persistence.*;
import java.util.UUID;

/** ERD restriction plus the role/department scope already used by develop's US21 SQL. */
@Entity @Table(name="room_restriction")
public class RoomRestriction {
    @Id @Column(name="restriction_id",length=50) public String restrictionId;
    @Column(name="room_id") public String roomId;
    @Column(name="user_id") public String userId;
    @Column(name="role_id") public String roleId;
    @Column(name="department_id") public String departmentId;
    @Column(columnDefinition="TEXT") public String notes;
    @PrePersist void initialize(){if(restrictionId==null)restrictionId=UUID.randomUUID().toString();}
}
