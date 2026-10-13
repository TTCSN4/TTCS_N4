
package com.ttcs.meetingmanagement.report.cancellation;

import java.time.LocalDate;
import java.util.List;

public record MeetingCancellationResponse(
        LocalDate from,
        LocalDate to,
        String timeBasis,
        String roomId,
        String organizerId,
        long totalMeetings,
        long cancelledMeetings,
        double cancellationRate,
        List<DailySummary> byDate,
        List<RoomSummary> byRoom,
        List<OrganizerSummary> byOrganizer
) {

    public record DailySummary(
            LocalDate date,
            long totalMeetings,
            long cancelledMeetings,
            double cancellationRate
    ) {
    }

    public record RoomSummary(
            String roomId,
            String roomName,
            long totalMeetings,
            long cancelledMeetings,
            double cancellationRate
    ) {
    }

    public record OrganizerSummary(
            String organizerId,
            String organizerName,
            long totalMeetings,
            long cancelledMeetings,
            double cancellationRate
    ) {
    }
}
