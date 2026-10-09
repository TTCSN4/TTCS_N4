
package com.ttcs.meetingmanagement.dto;

import java.time.LocalDateTime;

public record MobileMeetingSummaryResponse(
        String meetingId,
        String title,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String status,
        String roomName,
        boolean organizer
) {
}
