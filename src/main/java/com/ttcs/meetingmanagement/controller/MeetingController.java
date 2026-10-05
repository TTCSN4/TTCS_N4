package com.ttcs.meetingmanagement.controller;

import com.ttcs.meetingmanagement.model.Meeting;
import com.ttcs.meetingmanagement.model.MeetingStatus;
import com.ttcs.meetingmanagement.model.EquipmentBooking;
import com.ttcs.meetingmanagement.model.EquipmentBookingStatus;
import com.ttcs.meetingmanagement.repository.EquipmentBookingRepository;
import com.ttcs.meetingmanagement.repository.MeetingRepository;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/meetings")
public class MeetingController {
    private final MeetingRepository meetings;
    private final EquipmentBookingRepository equipmentBookings;

    public MeetingController(MeetingRepository meetings, EquipmentBookingRepository equipmentBookings) {
        this.meetings = meetings;
        this.equipmentBookings = equipmentBookings;
    }

    @GetMapping
    public List<Meeting> list() {
        return meetings.findAllByOrderByStartTimeAsc();
    }

    @GetMapping("/history")
    public List<Meeting> history() {
        return meetings.findAllByEndTimeBeforeOrderByStartTimeDesc(OffsetDateTime.now());
    }

    @PostMapping
    @Transactional
    public Meeting create(@RequestBody MeetingRequest request) {
        validate(request);
        List<String> participants = cleanParticipants(request.participants());
        ensureNoConflict(request.startTime(), request.endTime(), participants, null);

        Meeting first = save(request, participants, null, null);
        int repeatCount = Math.clamp(request.repeatCount() == null ? 0 : request.repeatCount(), 0, 12);
        String recurrence = request.recurrence() == null ? "NONE" : request.recurrence().toUpperCase(Locale.ROOT);
        if (repeatCount > 0 && !List.of("WEEKLY", "MONTHLY").contains(recurrence)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tần suất lặp không hợp lệ.");
        }

        if (repeatCount > 0) {
            first.setRecurrenceSeriesId(first.getId());
            meetings.save(first);
            for (int occurrence = 1; occurrence <= repeatCount; occurrence++) {
                OffsetDateTime start = recurrence.equals("WEEKLY")
                        ? request.startTime().plusWeeks(occurrence)
                        : request.startTime().plusMonths(occurrence);
                OffsetDateTime end = start.plusSeconds(request.endTime().toEpochSecond() - request.startTime().toEpochSecond());
                ensureNoConflict(start, end, participants, null);
                save(request, participants, start, first.getId());
            }
        }
        return first;
    }

    @PutMapping("/{id}")
    public Meeting update(@PathVariable Long id, @RequestBody MeetingRequest request) {
        validate(request);
        Meeting meeting = findMeeting(id);
        List<String> participants = cleanParticipants(request.participants());
        ensureNoConflict(request.startTime(), request.endTime(), participants, id);
        meeting.setTitle(request.title().trim());
        meeting.setDescription(request.description());
        meeting.setRoom(cleanRoom(request.room()));
        meeting.setStartTime(request.startTime());
        meeting.setEndTime(request.endTime());
        meeting.setOrganizerId(request.organizerId() == null ? 1L : request.organizerId());
        meeting.setParticipants(participants);
        meeting.setStatus(MeetingStatus.SCHEDULED);
        meeting.setUpdatedAt(OffsetDateTime.now());
        return meetings.save(meeting);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public void cancel(@PathVariable Long id) {
        Meeting meeting = findMeeting(id);
        meeting.setStatus(MeetingStatus.CANCELLED);
        meeting.setUpdatedAt(OffsetDateTime.now());
        meetings.save(meeting);
        for (EquipmentBooking booking : equipmentBookings.findAllByMeetingId(id)) {
            booking.setStatus(EquipmentBookingStatus.CANCELLED);
            equipmentBookings.save(booking);
        }
    }

    @PostMapping("/suggestions")
    public List<TimeSuggestion> suggestions(@RequestBody SuggestionRequest request) {
        if (request.date() == null || request.durationMinutes() == null || request.durationMinutes() < 15) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chọn ngày và thời lượng ít nhất 15 phút.");
        }
        List<String> participants = cleanParticipants(request.participants());
        List<TimeSuggestion> available = new ArrayList<>();
        for (int hour = 9; hour < 17 && available.size() < 5; hour++) {
            for (int minute : List.of(0, 30)) {
                OffsetDateTime start = request.date().atTime(hour, minute).atZone(ZoneId.systemDefault()).toOffsetDateTime();
                OffsetDateTime end = start.plusMinutes(request.durationMinutes());
                if (start.isAfter(OffsetDateTime.now()) && !hasConflict(start, end, participants, null)) {
                    available.add(new TimeSuggestion(start, end));
                }
                if (available.size() == 5) break;
            }
        }
        return available;
    }

    private Meeting save(MeetingRequest request, List<String> participants, OffsetDateTime start, Long seriesId) {
        OffsetDateTime now = OffsetDateTime.now();
        Meeting meeting = new Meeting();
        meeting.setTitle(request.title().trim());
        meeting.setDescription(request.description());
        meeting.setRoom(cleanRoom(request.room()));
        meeting.setStartTime(start == null ? request.startTime() : start);
        meeting.setEndTime(start == null ? request.endTime() : start.plusSeconds(request.endTime().toEpochSecond() - request.startTime().toEpochSecond()));
        meeting.setOrganizerId(request.organizerId() == null ? 1L : request.organizerId());
        meeting.setStatus(MeetingStatus.SCHEDULED);
        meeting.setRecurrenceSeriesId(seriesId);
        meeting.setCreatedAt(now);
        meeting.setUpdatedAt(now);
        meeting.setParticipants(new ArrayList<>(participants));
        return meetings.save(meeting);
    }

    private void validate(MeetingRequest request) {
        if (request.title() == null || request.title().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng nhập tiêu đề cuộc họp.");
        }
        if (request.startTime() == null || request.endTime() == null || !request.endTime().isAfter(request.startTime())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thời gian kết thúc phải sau thời gian bắt đầu.");
        }
    }

    private List<String> cleanParticipants(List<String> participants) {
        if (participants == null) return new ArrayList<>();
        return participants.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private String cleanRoom(String room) {
        return room == null || room.isBlank() ? null : room.trim();
    }

    private void ensureNoConflict(OffsetDateTime start, OffsetDateTime end, List<String> participants, Long ignoredId) {
        if (hasConflict(start, end, participants, ignoredId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Người tham dự đã có lịch trong khoảng thời gian này.");
        }
    }

    private boolean hasConflict(OffsetDateTime start, OffsetDateTime end, List<String> participants, Long ignoredId) {
        if (participants.isEmpty()) return false;
        return meetings.findAll().stream()
                .filter(meeting -> !meeting.getId().equals(ignoredId))
                .filter(meeting -> meeting.getStatus() == MeetingStatus.SCHEDULED)
                .filter(meeting -> meeting.getStartTime().isBefore(end) && meeting.getEndTime().isAfter(start))
                .anyMatch(meeting -> meeting.getParticipants().stream().anyMatch(participants::contains));
    }

    private Meeting findMeeting(Long id) {
        return meetings.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy cuộc họp."));
    }

    public record MeetingRequest(
            String title,
            String description,
            OffsetDateTime startTime,
            OffsetDateTime endTime,
            Long organizerId,
            List<String> participants,
            String recurrence,
            Integer repeatCount,
            String room
    ) { }

    public record SuggestionRequest(LocalDate date, Integer durationMinutes, List<String> participants) { }
    public record TimeSuggestion(OffsetDateTime startTime, OffsetDateTime endTime) { }
}