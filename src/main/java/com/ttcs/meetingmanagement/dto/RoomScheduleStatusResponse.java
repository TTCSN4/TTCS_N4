package com.ttcs.meetingmanagement.dto;

public record RoomScheduleStatusResponse(
        String roomId,
        String roomName,
        Integer capacity,
        String location,
        String status,
        boolean isAvailable,
        String reason
) {
}
