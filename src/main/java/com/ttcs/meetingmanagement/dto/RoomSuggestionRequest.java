
package com.ttcs.meetingmanagement.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record RoomSuggestionRequest(

        @NotNull(message = "Participant count is required")
        @Min(value = 1, message = "At least 1 participant is required")
        Integer participantCount,

        @NotNull(message = "Start time is required")
        LocalDateTime startTime,

        @NotNull(message = "End time is required")
        LocalDateTime endTime

) {
}
