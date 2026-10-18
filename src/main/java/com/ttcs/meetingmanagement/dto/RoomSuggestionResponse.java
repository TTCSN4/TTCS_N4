
package com.ttcs.meetingmanagement.dto;

public record RoomSuggestionResponse(

        String roomId,

        String roomName,

        Integer capacity,

        String status,

        Integer spareCapacity

) {
}
