package com.ttcs.meetingmanagement.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "meeting_participant")
public class MeetingParticipant {

    @Id
    @Column(name = "participant_id", nullable = false, length = 50)
    private String participantId;

    @Column(name = "meeting_id", nullable = false, length = 50)
    private String meetingId;

    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    @Column(name = "status", length = 50)
    private String status;

    @Column(name = "responded_at")
    private OffsetDateTime respondedAt;

    public MeetingParticipant() {
    }

    public MeetingParticipant(String participantId, String meetingId, String userId, String status, OffsetDateTime respondedAt) {
        this.participantId = participantId;
        this.meetingId = meetingId;
        this.userId = userId;
        this.status = status;
        this.respondedAt = respondedAt;
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

    public OffsetDateTime getRespondedAt() {
        return respondedAt;
    }

    public void setRespondedAt(OffsetDateTime respondedAt) {
        this.respondedAt = respondedAt;
    }
}
