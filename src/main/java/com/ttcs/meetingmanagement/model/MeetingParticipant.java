package com.ttcs.meetingmanagement.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "meeting_participant", uniqueConstraints = @UniqueConstraint(name = "uq_meeting_participant", columnNames = {"meeting_id", "user_id"}))
public class MeetingParticipant {

    @Id
    @Column(name = "participant_id", length = 50)
    private String participantId;

    @Column(name = "meeting_id", nullable = false, length = 50)
    private String meetingId;

    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    @Column(name = "status", nullable = false, length = 50)
    private String status = "PENDING";

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @PrePersist
    void initialize() {
        if (participantId == null || participantId.isBlank()) {
            participantId = UUID.randomUUID().toString();
        }
        if (status == null || status.isBlank()) {
            status = "PENDING";
        }
    }

    public MeetingParticipant() {
    }

    public MeetingParticipant(String meetingId, String userId) {
        this.meetingId = meetingId;
        this.userId = userId;
        this.status = "PENDING";
    }

    public String getParticipantId() {
        return participantId;
    }

    public void setParticipantId(String participantId) {
        this.participantId = participantId;
    }

    public String getMeetingId() {
        return meetingId;
    }

    public void setMeetingId(String meetingId) {
        this.meetingId = meetingId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getRespondedAt() {
        return respondedAt;
    }

    public void setRespondedAt(LocalDateTime respondedAt) {
        this.respondedAt = respondedAt;
    }
}
