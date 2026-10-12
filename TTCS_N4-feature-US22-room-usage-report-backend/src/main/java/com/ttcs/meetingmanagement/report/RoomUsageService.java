
package com.ttcs.meetingmanagement.report;

import com.ttcs.meetingmanagement.report.RoomUsageRepository.RoomRow;
import com.ttcs.meetingmanagement.report.RoomUsageRepository.MeetingRow;
import com.ttcs.meetingmanagement.report.RoomUsageResponse.RoomSummary;
import com.ttcs.meetingmanagement.report.RoomUsageResponse.DailySummary;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RoomUsageService {

    private static final long MAX_DAYS = 366;

    private final RoomUsageRepository repository;

    public RoomUsageService(RoomUsageRepository repository) {
        this.repository = repository;
    }

    private record Interval(
            LocalDateTime start,
            LocalDateTime end
    ) {
    }

    private static class DayTotal {
        long bookingCount = 0;
        long usedMillis = 0;
    }

    @Transactional(readOnly = true)
    public RoomUsageResponse generateReport(
            LocalDate from,
            LocalDate to,
            String roomId
    ) {

        // 1. Kiem tra tham so
        if (from == null || to == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "From and to dates are required"
            );
        }

        if (to.isBefore(from)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid date range"
            );
        }

        long days = ChronoUnit.DAYS.between(from, to) + 1;

        if (days > MAX_DAYS) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Maximum report range is 366 days"
            );
        }

        if (roomId != null && roomId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Room ID cannot be blank"
            );
        }

        String selectedRoom =
                roomId == null ? null : roomId.trim();

        LocalDateTime periodStart = from.atStartOfDay();
        LocalDateTime periodEnd = to.plusDays(1).atStartOfDay();

        // 2. Doc du lieu theo ERD
        List<RoomRow> rooms =
                repository.findRooms(selectedRoom);

        if (selectedRoom != null && rooms.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Room not found"
            );
        }

        List<MeetingRow> meetings =
                repository.findMeetings(
                        periodStart,
                        periodEnd,
                        selectedRoom
                );

        Map<String, List<MeetingRow>> byRoom =
                meetings.stream().collect(
                        Collectors.groupingBy(MeetingRow::roomId)
                );

        // 3. Tao danh sach thong ke tung ngay
        Map<LocalDate, DayTotal> dailyTotals =
                new LinkedHashMap<>();

        for (LocalDate day = from;
             !day.isAfter(to);
             day = day.plusDays(1)) {

            dailyTotals.put(day, new DayTotal());
        }

        List<RoomSummary> roomSummaries =
                new ArrayList<>();

        long totalBookings = 0;
        long totalUsedMillis = 0;

        long availablePerRoom = Duration.between(
                periodStart,
                periodEnd
        ).toMillis();

        // 4. Tinh thong ke tung phong
        for (RoomRow room : rooms) {

            List<MeetingRow> roomMeetings =
                    byRoom.getOrDefault(
                            room.roomId(),
                            List.of()
                    );

            totalBookings += roomMeetings.size();

            Map<LocalDate, List<Interval>> intervalsByDay =
                    new HashMap<>();

            for (MeetingRow meeting : roomMeetings) {

                // Gioi han lich hop vao khoang bao cao
                LocalDateTime start = later(
                        meeting.startTime(),
                        periodStart
                );

                LocalDateTime end = earlier(
                        meeting.endTime(),
                        periodEnd
                );

                if (!start.isBefore(end)) {
                    continue;
                }

                // Cat lich hop theo tung ngay
                LocalDate day = start.toLocalDate();

                while (day.atStartOfDay().isBefore(end)) {

                    LocalDateTime dayStart = later(
                            start,
                            day.atStartOfDay()
                    );

                    LocalDateTime dayEnd = earlier(
                            end,
                            day.plusDays(1).atStartOfDay()
                    );

                    if (dayStart.isBefore(dayEnd)) {

                        intervalsByDay
                                .computeIfAbsent(
                                        day,
                                        key -> new ArrayList<>()
                                )
                                .add(new Interval(
                                        dayStart,
                                        dayEnd
                                ));

                        DayTotal counter = dailyTotals.get(day);

                        if (counter != null) {
                            counter.bookingCount++;
                        }
                    }

                    day = day.plusDays(1);
                }
            }

            // Gop cac lich trung thoi gian
            long roomUsedMillis = 0;

            for (Map.Entry<LocalDate, List<Interval>> entry
                    : intervalsByDay.entrySet()) {

                long used = mergedMillis(entry.getValue());

                roomUsedMillis += used;

                DayTotal counter =
                        dailyTotals.get(entry.getKey());

                if (counter != null) {
                    counter.usedMillis += used;
                }
            }

            totalUsedMillis += roomUsedMillis;

            roomSummaries.add(new RoomSummary(
                    room.roomId(),
                    room.roomName(),
                    roomMeetings.size(),
                    toHours(roomUsedMillis),
                    toHours(availablePerRoom),
                    toPercent(
                            roomUsedMillis,
                            availablePerRoom
                    )
            ));
        }

        // 5. Thong ke theo ngay cho bieu do
        List<DailySummary> dailySummaries =
                new ArrayList<>();

        long availablePerDay =
                Duration.ofDays(1).toMillis()
                        * rooms.size();

        for (Map.Entry<LocalDate, DayTotal> entry
                : dailyTotals.entrySet()) {

            DayTotal counter = entry.getValue();

            dailySummaries.add(new DailySummary(
                    entry.getKey(),
                    counter.bookingCount,
                    toHours(counter.usedMillis),
                    toHours(availablePerDay),
                    toPercent(
                            counter.usedMillis,
                            availablePerDay
                    )
            ));
        }

        // 6. Thong ke tong hop
        long totalAvailable =
                availablePerRoom * rooms.size();

        return new RoomUsageResponse(
                from,
                to,
                "CALENDAR_24_HOURS_PER_DAY",
                totalBookings,
                toHours(totalUsedMillis),
                toHours(totalAvailable),
                toPercent(
                        totalUsedMillis,
                        totalAvailable
                ),
                roomSummaries,
                dailySummaries
        );
    }

    // Gop cac khoang thoi gian bi trung
    private long mergedMillis(List<Interval> intervals) {

        if (intervals.isEmpty()) {
            return 0;
        }

        List<Interval> sorted = new ArrayList<>(intervals);

        sorted.sort(
                Comparator.comparing(Interval::start)
        );

        LocalDateTime currentStart =
                sorted.get(0).start();

        LocalDateTime currentEnd =
                sorted.get(0).end();

        long total = 0;

        for (int i = 1; i < sorted.size(); i++) {

            Interval next = sorted.get(i);

            if (!next.start().isAfter(currentEnd)) {
                currentEnd = later(
                        currentEnd,
                        next.end()
                );
            } else {
                total += Duration.between(
                        currentStart,
                        currentEnd
                ).toMillis();

                currentStart = next.start();
                currentEnd = next.end();
            }
        }

        total += Duration.between(
                currentStart,
                currentEnd
        ).toMillis();

        return total;
    }

    private LocalDateTime later(
            LocalDateTime a,
            LocalDateTime b
    ) {
        return a.isAfter(b) ? a : b;
    }

    private LocalDateTime earlier(
            LocalDateTime a,
            LocalDateTime b
    ) {
        return a.isBefore(b) ? a : b;
    }

    private double toHours(long milliseconds) {
        return BigDecimal.valueOf(milliseconds)
                .divide(
                        BigDecimal.valueOf(3_600_000),
                        2,
                        RoundingMode.HALF_UP
                )
                .doubleValue();
    }

    private double toPercent(
            long used,
            long available
    ) {
        if (available <= 0) {
            return 0;
        }

        return BigDecimal.valueOf(used)
                .multiply(BigDecimal.valueOf(100))
                .divide(
                        BigDecimal.valueOf(available),
                        2,
                        RoundingMode.HALF_UP
                )
                .doubleValue();
    }
}
