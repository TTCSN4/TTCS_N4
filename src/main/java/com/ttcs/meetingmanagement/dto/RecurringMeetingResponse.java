
package com.ttcs.meetingmanagement.dto;

import java.util.List;

public record RecurringMeetingResponse(
        String recurrenceSeriesId,
        String recurrenceRule,
        int createdCount,
        List<String> meetingIds
) {
}
