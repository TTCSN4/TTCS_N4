package com.ttcs.meetingmanagement.dto;

import java.util.List;

public record RoomAvailabilitySummaryResponse(
        int totalRooms,
        int availableRoomsCount,
        int busyRoomsCount,
        int maintenanceRoomsCount,
        List<RoomScheduleStatusResponse> rooms
) {
}
