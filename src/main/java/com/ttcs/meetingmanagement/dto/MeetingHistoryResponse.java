

package com.ttcs.meetingmanagement.dto;

import java.time.LocalDateTime;

public record MeetingHistoryResponse(
        String meetingId,
        String title,
        String description,
        String roomId,
        String organizerId,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String status
) {
}
