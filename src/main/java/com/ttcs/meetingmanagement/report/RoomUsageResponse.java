
package com.ttcs.meetingmanagement.report;

import java.time.LocalDate;
import java.util.List;

public record RoomUsageResponse(
        LocalDate from,
        LocalDate to,
        String calculationBasis,
        long bookingCount,
        double usedHours,
        double availableHours,
        double utilizationPercent,
        List<RoomSummary> rooms,
        List<DailySummary> daily
) {

    public record RoomSummary(
            String roomId,
            String roomName,
            long bookingCount,
            double usedHours,
            double availableHours,
            double utilizationPercent
    ) {
    }

    public record DailySummary(
            LocalDate date,
            long bookingCount,
            double usedHours,
            double availableHours,
            double utilizationPercent
    ) {
    }
}
