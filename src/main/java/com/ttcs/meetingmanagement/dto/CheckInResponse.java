
package com.ttcs.meetingmanagement.dto;

import java.time.LocalDateTime;

public record CheckInResponse(
        String logId,
        String meetingId,
        String userId,
        LocalDateTime checkInTime,
        LocalDateTime checkOutTime,
        String status
) {
}
