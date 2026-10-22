
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
