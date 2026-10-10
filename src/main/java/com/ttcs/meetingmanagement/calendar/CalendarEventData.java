
package com.ttcs.meetingmanagement.calendar;

import java.time.OffsetDateTime;

public record CalendarEventData(
        String title,
        String description,
        String location,
        OffsetDateTime startTime,
        OffsetDateTime endTime
) {
    public CalendarEventData {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException(
                    "Meeting title is required");
        }

        if (startTime == null || endTime == null
                || !endTime.isAfter(startTime)) {
            throw new IllegalArgumentException(
                    "Invalid meeting time");
        }
    }
}
