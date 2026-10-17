
package com.ttcs.meetingmanagement.dto;

import jakarta.validation.constraints.NotBlank;

public class RoomBookingRequest {

    @NotBlank(message = "Meeting ID is required")
    private String meetingId;

    @NotBlank(message = "Room ID is required")
    private String roomId;

    public RoomBookingRequest() {
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
