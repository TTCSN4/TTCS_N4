package com.ttcs.meetingmanagement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class RoomBookingRequest {

    @NotNull(message = "meetingId is required")
    private String meetingId;

    @NotBlank(message = "roomId is required")
    private String roomId;

    public RoomBookingRequest() {
    }

    public RoomBookingRequest(String meetingId, String roomId) {
        this.meetingId = meetingId;
        this.roomId = roomId;
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
}