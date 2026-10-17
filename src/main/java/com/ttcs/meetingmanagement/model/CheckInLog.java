package com.ttcs.meetingmanagement.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "check_in_log")
public class CheckInLog {

    @Id
    @Column(name = "log_id", nullable = false, length = 50)
    private String logId;

    @Column(name = "meeting_id", nullable = false, length = 50)
    private String meetingId;

    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    @Column(name = "check_in_time")
    private OffsetDateTime checkInTime;

    @Column(name = "check_out_time")
    private OffsetDateTime checkOutTime;

    @Column(name = "qr_code_data", columnDefinition = "TEXT")
    private String qrCodeData;

    public CheckInLog() {
    }

    public CheckInLog(String logId, String meetingId, String userId, OffsetDateTime checkInTime,
                      OffsetDateTime checkOutTime, String qrCodeData) {
        this.logId = logId;
        this.meetingId = meetingId;
        this.userId = userId;
        this.checkInTime = checkInTime;
        this.checkOutTime = checkOutTime;
        this.qrCodeData = qrCodeData;
    }

    public String getLogId() {
        return logId;
    }

    public void setLogId(String logId) {
        this.logId = logId;
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

    public OffsetDateTime getCheckInTime() {
        return checkInTime;
    }

    public void setCheckInTime(OffsetDateTime checkInTime) {
        this.checkInTime = checkInTime;
    }

    public OffsetDateTime getCheckOutTime() {
        return checkOutTime;
    }

    public void setCheckOutTime(OffsetDateTime checkOutTime) {
        this.checkOutTime = checkOutTime;
    }

    public String getQrCodeData() {
        return qrCodeData;
    }

    public void setQrCodeData(String qrCodeData) {
        this.qrCodeData = qrCodeData;
    }
}
