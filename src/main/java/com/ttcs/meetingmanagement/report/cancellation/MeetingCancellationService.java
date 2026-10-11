
package com.ttcs.meetingmanagement.report.cancellation;

import com.ttcs.meetingmanagement.report.cancellation.MeetingCancellationRepository.MeetingRow;
import com.ttcs.meetingmanagement.report.cancellation.MeetingCancellationResponse.DailySummary;
import com.ttcs.meetingmanagement.report.cancellation.MeetingCancellationResponse.RoomSummary;
import com.ttcs.meetingmanagement.report.cancellation.MeetingCancellationResponse.OrganizerSummary;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MeetingCancellationService {

    private static final long MAX_DAYS = 366;

    private final MeetingCancellationRepository repository;

    public MeetingCancellationService(
            MeetingCancellationRepository repository) {
        this.repository = repository;
    }

    // Luu tong so cuoc hop va so cuoc hop bi huy
    private static class Counter {
        long total;
        long cancelled;

        void add(boolean isCancelled) {
            total++;

            if (isCancelled) {
                cancelled++;
            }
        }
    }

    // Bo dem co ten phong hoac ten nguoi to chuc
    private static class NamedCounter extends Counter {
        final String name;

        NamedCounter(String name) {
            this.name = name;
        }
    }

    @Transactional(readOnly = true)
    public MeetingCancellationResponse generateReport(
            LocalDate from,
            LocalDate to,
            String roomId,
            String organizerId
    ) {

        // 1. Kiem tra ngay bat dau va ngay ket thuc
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

        // 2. Chuan hoa tham so loc
        String selectedRoom = normalize(roomId);
        String selectedOrganizer = normalize(organizerId);

        if (roomId != null && selectedRoom == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Room ID cannot be blank"
            );
        }

        if (organizerId != null && selectedOrganizer == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Organizer ID cannot be blank"
            );
        }

        // 3. Kiem tra phong va nguoi to chuc ton tai
        if (selectedRoom != null
                && !repository.roomExists(selectedRoom)) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Room not found"
            );
        }

        if (selectedOrganizer != null
                && !repository.organizerExists(selectedOrganizer)) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Organizer not found"
            );
        }

        // 4. Khoang thoi gian bao gom ca ngay 'to'
        LocalDateTime periodStart = from.atStartOfDay();
        LocalDateTime periodEnd = to.plusDays(1).atStartOfDay();

        // 5. Lay du lieu tu Repository theo ERD
        List<MeetingRow> meetings = repository.findMeetings(
                periodStart,
                periodEnd,
                selectedRoom,
                selectedOrganizer
        );

        // 6. Khoi tao thong ke theo tung ngay
        Map<LocalDate, Counter> dateCounters =
                new LinkedHashMap<>();

        for (LocalDate day = from;
             !day.isAfter(to);
             day = day.plusDays(1)) {

            dateCounters.put(day, new Counter());
        }

        Map<String, NamedCounter> roomCounters =
                new LinkedHashMap<>();

        Map<String, NamedCounter> organizerCounters =
                new LinkedHashMap<>();

        Counter overall = new Counter();

        // 7. Duyet cac cuoc hop va tong hop so lieu
        for (MeetingRow meeting : meetings) {

            boolean cancelled = isCancelled(meeting.status());

            // Tong hop toan bo
            overall.add(cancelled);

            // Tong hop theo ngay bat dau cuoc hop
            LocalDate meetingDate =
                    meeting.startTime().toLocalDate();

            Counter daily = dateCounters.get(meetingDate);

            if (daily != null) {
                daily.add(cancelled);
            }

            // Tong hop theo phong
            String currentRoomId = meeting.roomId();

            String currentRoomName =
                    meeting.roomName() == null
                            ? "Chua gan phong"
                            : meeting.roomName();

            roomCounters.computeIfAbsent(
                    currentRoomId,
                    id -> new NamedCounter(currentRoomName)
            ).add(cancelled);

            // Tong hop theo nguoi to chuc
            String currentOrganizerId = meeting.organizerId();

            String currentOrganizerName =
                    meeting.organizerName() == null
                            ? "Khong ro nguoi to chuc"
                            : meeting.organizerName();

            organizerCounters.computeIfAbsent(
                    currentOrganizerId,
                    id -> new NamedCounter(currentOrganizerName)
            ).add(cancelled);
        }

        // 8. Chuyen thong ke theo ngay thanh DTO
        List<DailySummary> byDate = new ArrayList<>();

        for (Map.Entry<LocalDate, Counter> entry
                : dateCounters.entrySet()) {

            Counter counter = entry.getValue();

            byDate.add(new DailySummary(
                    entry.getKey(),
                    counter.total,
                    counter.cancelled,
                    calculateRate(counter)
            ));
        }

        // 9. Chuyen thong ke theo phong thanh DTO
        List<RoomSummary> byRoom = new ArrayList<>();

        for (Map.Entry<String, NamedCounter> entry
                : roomCounters.entrySet()) {

            NamedCounter counter = entry.getValue();

            byRoom.add(new RoomSummary(
                    entry.getKey(),
                    counter.name,
                    counter.total,
                    counter.cancelled,
                    calculateRate(counter)
            ));
        }

        // 10. Chuyen thong ke theo nguoi to chuc thanh DTO
        List<OrganizerSummary> byOrganizer =
                new ArrayList<>();

        for (Map.Entry<String, NamedCounter> entry
                : organizerCounters.entrySet()) {

            NamedCounter counter = entry.getValue();

            byOrganizer.add(new OrganizerSummary(
                    entry.getKey(),
                    counter.name,
                    counter.total,
                    counter.cancelled,
                    calculateRate(counter)
            ));
        }

        // 11. Tra ve bao cao hoan chinh
        return new MeetingCancellationResponse(
                from,
                to,
                "MEETING_START_TIME",
                selectedRoom,
                selectedOrganizer,
                overall.total,
                overall.cancelled,
                calculateRate(overall),
                byDate,
                byRoom,
                byOrganizer
        );
    }

    // Loai bo khoang trang dau va cuoi
    private String normalize(String value) {

        if (value == null) {
            return null;
        }

        String result = value.trim();

        return result.isEmpty() ? null : result;
    }

    // Chi tinh cuoc hop co status = CANCELLED
    private boolean isCancelled(String status) {

        return status != null
                && "CANCELLED".equalsIgnoreCase(status.trim());
    }

    // Ty le huy = so cuoc hop bi huy / tong so * 100
    private double calculateRate(Counter counter) {

        if (counter.total == 0) {
            return 0.0;
        }

        return BigDecimal.valueOf(counter.cancelled)
                .multiply(BigDecimal.valueOf(100))
                .divide(
                        BigDecimal.valueOf(counter.total),
                        2,
                        RoundingMode.HALF_UP
                )
                .doubleValue();
    }
}
