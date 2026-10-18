
package com.ttcs.meetingmanagement.recurrence;

import org.junit.jupiter.api.Test;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

class RecurringScheduleCalculatorTest {

    private final RecurringScheduleCalculator calculator =
            new RecurringScheduleCalculator();

    @Test
    void shouldGenerateWeeklyMeetings() {
        OffsetDateTime start =
                OffsetDateTime.parse("2026-10-12T09:00:00+07:00");

        OffsetDateTime end =
                OffsetDateTime.parse("2026-10-12T10:00:00+07:00");

        var result = calculator.generate(
                start, end, RecurrenceFrequency.WEEKLY, 1, 4
        );

        assertEquals(4, result.size());
        assertEquals(start, result.get(0).startTime());
        assertEquals(start.plusWeeks(1), result.get(1).startTime());
        assertEquals(start.plusWeeks(2), result.get(2).startTime());
        assertEquals(start.plusWeeks(3), result.get(3).startTime());
    }

    @Test
    void shouldGenerateMonthlyMeetings() {
        OffsetDateTime start =
                OffsetDateTime.parse("2027-01-31T09:00:00+07:00");

        OffsetDateTime end =
                OffsetDateTime.parse("2027-01-31T10:00:00+07:00");

        var result = calculator.generate(
                start, end, RecurrenceFrequency.MONTHLY, 1, 3
        );

        assertEquals(3, result.size());
        assertEquals(start, result.get(0).startTime());

        assertEquals(
                OffsetDateTime.parse("2027-02-28T09:00:00+07:00"),
                result.get(1).startTime()
        );

        assertEquals(
                OffsetDateTime.parse("2027-03-31T09:00:00+07:00"),
                result.get(2).startTime()
        );
    }

    @Test
    void shouldRejectInvalidTime() {
        OffsetDateTime start =
                OffsetDateTime.parse("2026-10-12T10:00:00+07:00");

        OffsetDateTime end =
                OffsetDateTime.parse("2026-10-12T09:00:00+07:00");

        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.generate(
                        start, end, RecurrenceFrequency.WEEKLY, 1, 4
                )
        );
    }

    @Test
    void shouldRejectInvalidOccurrences() {
        OffsetDateTime start =
                OffsetDateTime.parse("2026-10-12T09:00:00+07:00");

        OffsetDateTime end =
                OffsetDateTime.parse("2026-10-12T10:00:00+07:00");

        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.generate(
                        start, end, RecurrenceFrequency.WEEKLY, 1, 0
                )
        );
    }
}