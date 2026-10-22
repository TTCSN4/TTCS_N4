package com.ttcs.meetingmanagement.recurrence;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class RecurringScheduleCalculator {

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