
package com.ttcs.meetingmanagement.dto;

import java.time.LocalDateTime;

public class RoomBookingResponse {

    private String meetingId;
    private String roomId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String message;

    public RoomBookingResponse() {
    }

    public RoomBookingResponse(
            String meetingId,
            String roomId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String message) {

        this.meetingId = meetingId;
        this.roomId = roomId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.message = message;
    }

    public String getMeetingId() {
        return meetingId;
    }

    public void setMeetingId(String meetingId) {
        this.meetingId = meetingId;
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
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

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
