package com.ttcs.meetingmanagement.recurrence;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import com.ttcs.meetingmanagement.dto.RecurringMeetingRequest;

public class RecurringScheduleCalculator {

    public List<Occurrence> generate(RecurringMeetingRequest request) {
        RecurringMeetingRules.validate(request);
        LocalDate first = request.startTime().toLocalDate();
        LocalDate until = request.until();
        if (until != null && until.isBefore(first)) throw new IllegalArgumentException("Ngày kết thúc trước ngày bắt đầu");
        List<Integer> days = request.weekdays();
        if (days == null) days = List.of(first.getDayOfWeek().getValue());
        if (days.isEmpty() || days.stream().anyMatch(d -> d == null || d < 1 || d > 7)) throw new IllegalArgumentException("Thứ trong tuần không hợp lệ");
        int monthDay = request.monthDay() == null ? first.getDayOfMonth() : request.monthDay();
        if (monthDay < 1 || monthDay > 31) throw new IllegalArgumentException("Ngày trong tháng không hợp lệ");
        int count = request.occurrences() == null ? 366 : request.occurrences();
        if (count > 366) throw new IllegalArgumentException("Tối đa 366 lần họp");
        Duration duration = Duration.between(request.startTime(), request.endTime());
        List<Occurrence> result = new ArrayList<>();
        LocalDate week = first.minusDays(first.getDayOfWeek().getValue() - 1);
        for (LocalDate date = first; result.size() < count && !date.isAfter(first.plusYears(100)); date = date.plusDays(1)) {
            if (until != null && date.isAfter(until)) break;
            boolean matches;
            if (request.frequency() == RecurrenceFrequency.WEEKLY) {
                long weeks = ChronoUnit.WEEKS.between(week, date.minusDays(date.getDayOfWeek().getValue() - 1));
                matches = weeks % request.interval() == 0 && days.contains(date.getDayOfWeek().getValue());
            } else {
                long months = ChronoUnit.MONTHS.between(YearMonth.from(first), YearMonth.from(date));
                matches = months % request.interval() == 0 && date.getDayOfMonth() == monthDay;
            }
            if (matches) {
                OffsetDateTime start = date.atTime(request.startTime().toLocalTime()).atOffset(request.startTime().getOffset());
                result.add(new Occurrence(start, start.plus(duration)));
            }
        }
        if (result.isEmpty()) throw new IllegalArgumentException("Không có ngày họp hợp lệ");
        return result;
    }

    public record Occurrence(
            OffsetDateTime startTime,
            OffsetDateTime endTime
    ) {
    }

    public List<Occurrence> generate(
            OffsetDateTime startTime,
            OffsetDateTime endTime,
            RecurrenceFrequency frequency,
            int interval,
            int occurrences
    ) {

        // 1. Kiểm tra dữ liệu đầu vào
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException(
                    "Thời gian bắt đầu và kết thúc không được để trống"
            );
        }

        if (frequency == null) {
            throw new IllegalArgumentException(
                    "Tần suất lặp không được để trống"
            );
        }

        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException(
                    "Thời gian kết thúc phải sau thời gian bắt đầu"
            );
        }

        if (interval < 1) {
            throw new IllegalArgumentException(
                    "Khoảng lặp phải lớn hơn hoặc bằng 1"
            );
        }

        if (occurrences < 1) {
            throw new IllegalArgumentException(
                    "Số lần họp phải lớn hơn hoặc bằng 1"
            );
        }

        // 2. Tính thời lượng cuộc họp
        Duration duration = Duration.between(
                startTime,
                endTime
        );

        List<Occurrence> result = new ArrayList<>();

        // 3. Sinh các lần họp
        for (int i = 0; i < occurrences; i++) {

            long step = (long) i * interval;

            OffsetDateTime nextStart;

            if (frequency == RecurrenceFrequency.WEEKLY) {
                nextStart = startTime.plusWeeks(step);
            } else {
                nextStart = startTime.plusMonths(step);
            }

            OffsetDateTime nextEnd =
                    nextStart.plus(duration);

            result.add(
                    new Occurrence(nextStart, nextEnd)
            );
        }

        // 4. Trả về danh sách lịch họp
        return result;
    }
}
