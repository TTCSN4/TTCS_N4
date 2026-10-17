package com.ttcs.meetingmanagement.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "calendar_sync")
public class CalendarSync {

    @Id
    @Column(name = "sync_id", nullable = false, length = 50)
    private String syncId;

    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    @Column(name = "meeting_id", length = 50)
    private String meetingId;

    @Column(name = "provider", length = 50)
    private String provider;

    @Column(name = "external_calendar_id", length = 255)
    private String externalCalendarId;

    @Column(name = "external_event_id", length = 255)
    private String externalEventId;

    @Column(name = "sync_status", length = 50)
    private String syncStatus;

    @Column(name = "last_synced_at")
    private OffsetDateTime lastSyncedAt;

    public CalendarSync() {
    }

    public CalendarSync(String syncId, String userId, String meetingId, String provider,
                        String externalCalendarId, String externalEventId, String syncStatus,
                        OffsetDateTime lastSyncedAt) {
        this.syncId = syncId;
        this.userId = userId;
        this.meetingId = meetingId;
        this.provider = provider;
        this.externalCalendarId = externalCalendarId;
        this.externalEventId = externalEventId;
        this.syncStatus = syncStatus;
        this.lastSyncedAt = lastSyncedAt;
    }

    public String getSyncId() {
        return syncId;
    }

    public void setSyncId(String syncId) {
        this.syncId = syncId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getMeetingId() {
        return meetingId;
    }

    public void setMeetingId(String meetingId) {
        this.meetingId = meetingId;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getExternalCalendarId() {
        return externalCalendarId;
    }

    public void setExternalCalendarId(String externalCalendarId) {
        this.externalCalendarId = externalCalendarId;
    }

    public String getExternalEventId() {
        return externalEventId;
    }

    public void setExternalEventId(String externalEventId) {
        this.externalEventId = externalEventId;
    }

    public String getSyncStatus() {
        return syncStatus;
    }

    public void setSyncStatus(String syncStatus) {
        this.syncStatus = syncStatus;
    }

    public OffsetDateTime getLastSyncedAt() {
        return lastSyncedAt;
    }

    public void setLastSyncedAt(OffsetDateTime lastSyncedAt) {
        this.lastSyncedAt = lastSyncedAt;
    }
}
