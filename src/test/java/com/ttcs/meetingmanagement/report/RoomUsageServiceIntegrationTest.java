
package com.ttcs.meetingmanagement.report;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RoomUsageServiceIntegrationTest {

    @Autowired
    private RoomUsageService service;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private final LocalDate from =
            LocalDate.of(2026, 10, 1);

    private final LocalDate to =
            LocalDate.of(2026, 10, 3);

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM meeting");
        jdbcTemplate.update("DELETE FROM room");

        jdbcTemplate.update("INSERT INTO room (room_id, room_name, capacity, location, status) VALUES ('R001', 'Phòng Họp 1', 10, 'Tầng 1', 'AVAILABLE')");
        jdbcTemplate.update("INSERT INTO room (room_id, room_name, capacity, location, status) VALUES ('R002', 'Phòng Họp 2', 10, 'Tầng 1', 'AVAILABLE')");
        jdbcTemplate.update("INSERT INTO room (room_id, room_name, capacity, location, status) VALUES ('R003', 'Phòng Họp 3', 10, 'Tầng 1', 'AVAILABLE')");

        // 4 bookings: 3 on R001 (1.5h * 3 = 4.5h), 1 on R002 (2.0h), total 6.5h across 3 days
        jdbcTemplate.update("INSERT INTO meeting (meeting_id, room_id, organizer_id, title, start_time, end_time, status) VALUES ('M_TEST_1', 'R001', 'USER001', 'Họp 1', '2026-10-01 09:00:00', '2026-10-01 10:30:00', 'SCHEDULED')");
        jdbcTemplate.update("INSERT INTO meeting (meeting_id, room_id, organizer_id, title, start_time, end_time, status) VALUES ('M_TEST_2', 'R001', 'USER001', 'Họp 2', '2026-10-02 09:00:00', '2026-10-02 10:30:00', 'SCHEDULED')");
        jdbcTemplate.update("INSERT INTO meeting (meeting_id, room_id, organizer_id, title, start_time, end_time, status) VALUES ('M_TEST_3', 'R001', 'USER001', 'Họp 3', '2026-10-03 09:00:00', '2026-10-03 10:30:00', 'SCHEDULED')");
        jdbcTemplate.update("INSERT INTO meeting (meeting_id, room_id, organizer_id, title, start_time, end_time, status) VALUES ('M_TEST_4', 'R002', 'USER001', 'Họp 4', '2026-10-02 14:00:00', '2026-10-02 16:00:00', 'SCHEDULED')");
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
