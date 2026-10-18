package com.ttcs.meetingmanagement;

import com.ttcs.meetingmanagement.recurrence.RecurrenceFrequency;
import com.ttcs.meetingmanagement.recurrence.RecurringScheduleCalculator;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecurringScheduleTest {

    private final RecurringScheduleCalculator calculator = new RecurringScheduleCalculator();

    @Test
    void weeklyMultipleDaysAndInterval() {
        OffsetDateTime start = OffsetDateTime.parse("2099-01-05T09:00:00+07:00");
        OffsetDateTime end = OffsetDateTime.parse("2099-01-05T10:00:00+07:00");

        var result = calculator.generate(start, end, RecurrenceFrequency.WEEKLY, 2, 4);

        assertEquals(4, result.size());
        assertEquals(
                List.of(
                        LocalDate.of(2099, 1, 5),
                        LocalDate.of(2099, 1, 19),
                        LocalDate.of(2099, 2, 2),
                        LocalDate.of(2099, 2, 16)
                ),
                result.stream().map(s -> s.startTime().toLocalDate()).toList()
        );
    }

    @Test
    void monthlyScheduleGeneration() {
        OffsetDateTime start = OffsetDateTime.parse("2099-01-31T09:00:00+07:00");
        OffsetDateTime end = OffsetDateTime.parse("2099-01-31T10:00:00+07:00");

        var result = calculator.generate(start, end, RecurrenceFrequency.MONTHLY, 1, 3);

        assertEquals(3, result.size());
        assertEquals(
                List.of(
                        LocalDate.of(2099, 1, 31),
                        LocalDate.of(2099, 2, 28),
                        LocalDate.of(2099, 3, 31)
                ),
                result.stream().map(s -> s.startTime().toLocalDate()).toList()
        );
    }

    @Test
    void invalidConfigurationRejection() {
        OffsetDateTime start = OffsetDateTime.parse("2099-01-05T09:00:00+07:00");
        OffsetDateTime end = OffsetDateTime.parse("2099-01-05T10:00:00+07:00");

        // startTime after endTime
        assertThrows(IllegalArgumentException.class, () ->
                calculator.generate(end, start, RecurrenceFrequency.WEEKLY, 1, 4));

        // interval < 1
        assertThrows(IllegalArgumentException.class, () ->
                calculator.generate(start, end, RecurrenceFrequency.WEEKLY, 0, 4));

        // occurrences < 1
        assertThrows(IllegalArgumentException.class, () ->
                calculator.generate(start, end, RecurrenceFrequency.MONTHLY, 1, 0));
    }
}
