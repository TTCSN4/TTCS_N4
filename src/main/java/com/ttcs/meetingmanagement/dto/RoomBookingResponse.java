package com.ttcs.meetingmanagement.dto;

import java.time.OffsetDateTime;

public class RoomBookingResponse {

    private Long meetingId;
    private String roomId;
    private OffsetDateTime startTime;
    private OffsetDateTime endTime;
    private String message;
    private String cancelReason;
    private OffsetDateTime cancelledAt;

    public RoomBookingResponse() {
    }

    public RoomBookingResponse(
            Long meetingId,
            String roomId,
            OffsetDateTime startTime,
            OffsetDateTime endTime,
            String message) {

        this(meetingId, roomId, startTime, endTime, message, null, null);
    }

    public RoomBookingResponse(
            Long meetingId,
            String roomId,
            OffsetDateTime startTime,
            OffsetDateTime endTime,
            String message,
            String cancelReason,
            OffsetDateTime cancelledAt) {

        this.meetingId = meetingId;
        this.roomId = roomId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.message = message;
        this.cancelReason = cancelReason;
        this.cancelledAt = cancelledAt;
    }

    public Long getMeetingId() {
        return meetingId;
    }

    public void setMeetingId(Long meetingId) {
        this.meetingId = meetingId;
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public OffsetDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(OffsetDateTime startTime) {
        this.startTime = startTime;
    }

    public OffsetDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(OffsetDateTime endTime) {
        this.endTime = endTime;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }

    public OffsetDateTime getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(OffsetDateTime cancelledAt) {
        this.cancelledAt = cancelledAt;
    }
}