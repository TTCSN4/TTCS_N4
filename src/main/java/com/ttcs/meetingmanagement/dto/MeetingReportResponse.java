
package com.ttcs.meetingmanagement.dto;

import java.time.LocalDateTime;

public record MeetingReportResponse(

    String meetingId,

    String title,

    String organizerId,

    String organizerName,

    String roomId,

    String roomName,

    LocalDateTime startTime,

    LocalDateTime endTime,

    String status,

    String cancelReason,

    LocalDateTime cancelledAt

) {
}
