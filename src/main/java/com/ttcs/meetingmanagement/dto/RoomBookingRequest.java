package com.ttcs.meetingmanagement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class RoomBookingRequest {

    @NotNull(message = "meetingId is required")
    private Long meetingId;

    @NotBlank(message = "roomId is required")
    private String roomId;

    public RoomBookingRequest() {
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
}