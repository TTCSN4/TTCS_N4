
package com.ttcs.meetingmanagement.dto;

import com.ttcs.meetingmanagement.recurrence.RecurrenceFrequency;
import java.time.OffsetDateTime;

public record RecurringMeetingRequest(
        String organizerId,
        String roomId,
        String title,
        String description,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        RecurrenceFrequency frequency,
        Integer interval,
        Integer occurrences
) {
}
