
package com.ttcs.meetingmanagement.dto;

import java.util.List;

public record MobileCalendarPageResponse(
        List<MobileMeetingSummaryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
