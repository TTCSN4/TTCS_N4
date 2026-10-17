package com.ttcs.meetingmanagement.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "notification")
public class Notification {

    @Id
    @Column(name = "notification_id", nullable = false, length = 50)
    private String notificationId;

    @Column(name = "meeting_id", length = 50)
    private String meetingId;

    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    @Column(name = "type", length = 50)
    private String type;

    @Column(name = "channel", length = 50)
    private String channel;

    @Column(name = "scheduled_at")
    private OffsetDateTime scheduledAt;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @Column(name = "status", length = 50)
    private String status;

    public Notification() {
    }

    public Notification(String notificationId, String meetingId, String userId, String type,
                        String channel, OffsetDateTime scheduledAt, OffsetDateTime sentAt, String status) {
        this.notificationId = notificationId;
        this.meetingId = meetingId;
        this.userId = userId;
        this.type = type;
        this.channel = channel;
        this.scheduledAt = scheduledAt;
        this.sentAt = sentAt;
        this.status = status;
    }

    public String getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(String notificationId) {
        this.notificationId = notificationId;
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public OffsetDateTime getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(OffsetDateTime scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    public OffsetDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(OffsetDateTime sentAt) {
        this.sentAt = sentAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
