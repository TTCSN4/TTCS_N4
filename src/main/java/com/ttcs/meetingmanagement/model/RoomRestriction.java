package com.ttcs.meetingmanagement.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "room_restriction")
public class RoomRestriction {

    @Id
    @Column(name = "restriction_id", nullable = false, length = 50)
    private String restrictionId;

    @Column(name = "room_id", nullable = false, length = 50)
    private String roomId;

    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    public RoomRestriction() {
    }

    public RoomRestriction(String restrictionId, String roomId, String userId, String notes) {
        this.restrictionId = restrictionId;
        this.roomId = roomId;
        this.userId = userId;
        this.notes = notes;
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

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
