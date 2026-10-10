
package com.ttcs.meetingmanagement.dto;

import com.ttcs.meetingmanagement.room.Room;

public record AvailableRoomResponse(
        String roomId,
        String roomName,
        Integer capacity,
        String location,
        String status
) {
    public static AvailableRoomResponse from(Room room) {
        return new AvailableRoomResponse(
                room.getRoomId(),
                room.getRoomName(),
                room.getCapacity(),
                room.getLocation(),
                room.getStatus()
        );
    }
}
