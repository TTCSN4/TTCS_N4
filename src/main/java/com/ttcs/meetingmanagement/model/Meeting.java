package com.ttcs.meetingmanagement.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "meeting")
public class Meeting {

    // Ma cuoc hop - String theo ERD
    @Id
    @Column(name = "meeting_id", length = 50)
    public String meetingId;

    // Nguoi to chuc
    @Column(name = "organizer_id", nullable = false, length = 50)
    public String organizerId;

    // Phong duoc dat
    @Column(name = "room_id", length = 50)
    public String roomId;

    @Column(name = "title", nullable = false, length = 255)
    public String title;

    @Column(name = "description", columnDefinition = "TEXT")
    public String description;

    // Thoi gian hop
    @Column(name = "start_time", nullable = false)
    public LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    public LocalDateTime endTime;

    // Trang thai cuoc hop
    @Column(name = "status", nullable = false, length = 50)
    public String status;

    // Lich hop dinh ky
    @Column(name = "is_recurring")
    public Boolean isRecurring = false;

    @Column(name = "recurrence_rule", length = 255)
    public String recurrenceRule;

    @Column(name = "recurrence_series_id", length = 50)
    public String recurrenceSeriesId;

    // Huy cuoc hop
    @Column(name = "cancel_reason", length = 500)
    public String cancelReason;

    @Column(name = "cancelled_at")
    public LocalDateTime cancelledAt;

    // Thoi gian tao va cap nhat
    @Column(name = "created_at")
    public LocalDateTime createdAt;

    @Column(name = "updated_at")
    public LocalDateTime updatedAt;

    @PrePersist
    void initialize() {
        if (meetingId == null) meetingId = UUID.randomUUID().toString();
        if (createdAt == null) createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = "SCHEDULED";
    }

    @PreUpdate
    void updateTimestamp() {
        updatedAt = LocalDateTime.now();
    }

    public Meeting() {
    }

    public String getMeetingId() {
        return meetingId;
    }

    public void setMeetingId(String meetingId) {
        this.meetingId = meetingId;
    }

    @JsonProperty("id")
    public String getId() {
        return meetingId;
    }

    public void setId(String id) {
        this.meetingId = id;
    }

    public String getOrganizerId() {
        return organizerId;
    }

    public void setOrganizerId(String organizerId) {
        this.organizerId = organizerId;
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    @JsonProperty("room")
    public String getRoom() {
        return roomId;
    }

    public void setRoom(String room) {
        this.roomId = room;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setStatus(MeetingStatus status) {
        this.status = status != null ? status.name() : null;
    }

    public Boolean getIsRecurring() {
        return isRecurring;
    }

    public void setIsRecurring(Boolean isRecurring) {
        this.isRecurring = isRecurring;
    }

    public String getRecurrenceRule() {
        return recurrenceRule;
    }

    public void setRecurrenceRule(String recurrenceRule) {
        this.recurrenceRule = recurrenceRule;
    }

    public String getRecurrenceSeriesId() {
        return recurrenceSeriesId;
    }

    public void setRecurrenceSeriesId(String recurrenceSeriesId) {
        this.recurrenceSeriesId = recurrenceSeriesId;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }

    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(LocalDateTime cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
