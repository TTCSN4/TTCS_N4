
package com.ttcs.meetingmanagement.report;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RoomUsageServiceIntegrationTest {

    @Autowired
    private RoomUsageService service;

    @Autowired
    private JdbcTemplate jdbc;

    private final LocalDate from =
            LocalDate.of(2026, 10, 1);

    private final LocalDate to =
            LocalDate.of(2026, 10, 3);

    @BeforeEach
    void setupData() {
        jdbc.execute("DELETE FROM meeting");
        jdbc.execute("DELETE FROM room");

        jdbc.execute("INSERT INTO room (room_id, room_name, capacity, location, status) VALUES ('R001', 'Phòng R001', 10, 'Tầng 1', 'AVAILABLE')");
        jdbc.execute("INSERT INTO room (room_id, room_name, capacity, location, status) VALUES ('R002', 'Phòng R002', 20, 'Tầng 1', 'AVAILABLE')");
        jdbc.execute("INSERT INTO room (room_id, room_name, capacity, location, status) VALUES ('R003', 'Phòng R003', 30, 'Tầng 1', 'AVAILABLE')");

        jdbc.execute("INSERT INTO meeting (meeting_id, title, room_id, organizer_id, start_time, end_time, status) VALUES ('M1', 'Meeting 1', 'R001', 'USER_1', '2026-10-01 09:00:00', '2026-10-01 10:30:00', 'SCHEDULED')");
        jdbc.execute("INSERT INTO meeting (meeting_id, title, room_id, organizer_id, start_time, end_time, status) VALUES ('M2', 'Meeting 2', 'R001', 'USER_1', '2026-10-02 09:00:00', '2026-10-02 10:30:00', 'SCHEDULED')");
        jdbc.execute("INSERT INTO meeting (meeting_id, title, room_id, organizer_id, start_time, end_time, status) VALUES ('M3', 'Meeting 3', 'R002', 'USER_1', '2026-10-02 14:00:00', '2026-10-02 16:00:00', 'SCHEDULED')");
        jdbc.execute("INSERT INTO meeting (meeting_id, title, room_id, organizer_id, start_time, end_time, status) VALUES ('M4', 'Meeting 4', 'R001', 'USER_1', '2026-10-03 09:00:00', '2026-10-03 10:30:00', 'SCHEDULED')");
    }

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
