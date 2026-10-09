
package com.ttcs.meetingmanagement.dto;

import java.time.LocalDateTime;

public record MobileMeetingDetailResponse(
        String meetingId,
        String title,
        String description,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String status,
        String organizerId,
        String roomId,
        String roomName,
        String roomLocation,
        boolean organizer
) {
}
