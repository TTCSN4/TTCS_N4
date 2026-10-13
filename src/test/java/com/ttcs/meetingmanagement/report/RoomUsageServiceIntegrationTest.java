
package com.ttcs.meetingmanagement.report;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@org.springframework.transaction.annotation.Transactional
class RoomUsageServiceIntegrationTest {
    @Autowired private com.ttcs.meetingmanagement.room.RoomRepository rooms;
    @Autowired private com.ttcs.meetingmanagement.repository.MeetingRepository meetings;

    @org.junit.jupiter.api.BeforeEach
    void createFixture() {
        for (String id : java.util.List.of("R001", "R002", "R003")) {
            var room = new com.ttcs.meetingmanagement.room.Room();
            room.setRoomId(id); room.setRoomName(id); room.setCapacity(10); room.setStatus("AVAILABLE");
            rooms.save(room);
        }
        for (int i = 0; i < 4; i++) {
            var m = new com.ttcs.meetingmanagement.model.Meeting();
            m.organizerId = "REPORT-USER"; m.roomId = i < 3 ? "R001" : "R002"; m.title = "Report fixture";
            m.startTime = java.time.LocalDateTime.of(2026, 10, i < 3 ? i + 1 : 1, 9, 0);
            m.endTime = m.startTime.plusMinutes(i < 3 ? 90 : 120); m.status = "SCHEDULED";
            meetings.save(m);
        }
        meetings.flush();
    }

    @Autowired
    private RoomUsageService service;

    private final LocalDate from =
            LocalDate.of(2026, 10, 1);

    private final LocalDate to =
            LocalDate.of(2026, 10, 3);

    @Test
    void shouldCalculateAllRoomsCorrectly() {

        RoomUsageResponse result =
                service.generateReport(from, to, null);

        assertEquals(4L, result.bookingCount());
        assertEquals(6.5, result.usedHours(), 0.01);
        assertEquals(216.0, result.availableHours(), 0.01);
        assertEquals(3.01, result.utilizationPercent(), 0.01);

        assertEquals(3, result.rooms().size());
        assertEquals(3, result.daily().size());
    }

    @Test
    void shouldFilterByRoomId() {

        RoomUsageResponse result =
                service.generateReport(from, to, "R001");

        assertEquals(3L, result.bookingCount());
        assertEquals(4.5, result.usedHours(), 0.01);
        assertEquals(72.0, result.availableHours(), 0.01);
        assertEquals(6.25, result.utilizationPercent(), 0.01);

        assertEquals(1, result.rooms().size());
        assertEquals("R001", result.rooms().get(0).roomId());
    }

    @Test
    void shouldRejectInvalidDateRange() {

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> service.generateReport(
                                LocalDate.of(2026, 10, 3),
                                LocalDate.of(2026, 10, 1),
                                null
                        )
                );

        assertEquals(400, exception.getStatusCode().value());
    }
}
