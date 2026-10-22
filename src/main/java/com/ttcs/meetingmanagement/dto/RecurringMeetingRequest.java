
package com.ttcs.meetingmanagement.dto;

import com.ttcs.meetingmanagement.recurrence.RecurrenceFrequency;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.List;

public record RecurringMeetingRequest(
        String organizerId,
        String roomId,
        String title,
        String description,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        RecurrenceFrequency frequency,
        Integer interval,
        Integer occurrences,
        List<Integer> weekdays,
        Integer monthDay,
        LocalDate until,
        List<String> participants
) {
    public RecurringMeetingRequest(String organizerId, String roomId, String title,
            String description, OffsetDateTime startTime, OffsetDateTime endTime,
            RecurrenceFrequency frequency, Integer interval, Integer occurrences) {
        this(organizerId, roomId, title, description, startTime, endTime,
                frequency, interval, occurrences, null, null, null, List.of());
    }
}
