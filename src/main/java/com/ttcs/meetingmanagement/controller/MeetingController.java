
package com.ttcs.meetingmanagement.controller;

import com.ttcs.meetingmanagement.model.Meeting;
import com.ttcs.meetingmanagement.repository.MeetingRepository;
import com.ttcs.meetingmanagement.service.RoomBookingPermissionService;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/meetings")
public class MeetingController {

    private final MeetingRepository meetings;
    private final RoomBookingPermissionService permissionService;
    private final NamedParameterJdbcTemplate jdbc;

    public MeetingController(
            MeetingRepository meetings,
            RoomBookingPermissionService permissionService,
            NamedParameterJdbcTemplate jdbc) {

        this.meetings = meetings;
        this.permissionService = permissionService;
        this.jdbc = jdbc;
    }

    // ==========================================
    // 1. LAY DANH SACH CUOC HOP
    // ==========================================

    @GetMapping
    public List<Meeting> list() {
        return meetings.findAllByOrderByStartTimeAsc();
    }

    // ==========================================
    // 2. LICH SU CUOC HOP
    // ==========================================

    @GetMapping("/history")
    public List<Meeting> history() {

        return meetings
                .findAllByEndTimeBeforeOrderByStartTimeDesc(
                        LocalDateTime.now()
                );
    }

    // ==========================================
    // 3. TAO CUOC HOP
    // ==========================================

    @PostMapping
    @Transactional
    public Meeting create(
            @RequestBody MeetingRequest request) {

        validate(request);

        List<String> participants =
                cleanParticipants(request.participants());

        int repeatCount =
                request.repeatCount() == null
                        ? 0
                        : request.repeatCount();

        if (repeatCount < 0 || repeatCount > 12) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Repeat count must be between 0 and 12"
            );
        }

        String recurrence =
                request.recurrence() == null
                        ? "NONE"
                        : request.recurrence()
                                .toUpperCase(Locale.ROOT);

        if (repeatCount > 0
                && !List.of("WEEKLY", "MONTHLY")
                        .contains(recurrence)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid recurrence rule"
            );
        }

        String roomId = cleanRoom(request.room());

        String seriesId =
                repeatCount > 0
                        ? UUID.randomUUID().toString()
                        : null;

        Meeting first = createOccurrence(
                request,
                participants,
                roomId,
                request.startTime(),
                request.endTime(),
                seriesId,
                recurrence
        );

        Duration duration = Duration.between(
                request.startTime(),
                request.endTime()
        );

        for (int i = 1; i <= repeatCount; i++) {

            LocalDateTime start =
                    recurrence.equals("WEEKLY")
                            ? request.startTime().plusWeeks(i)
                            : request.startTime().plusMonths(i);

            LocalDateTime end = start.plus(duration);

            createOccurrence(
                    request,
                    participants,
                    roomId,
                    start,
                    end,
                    seriesId,
                    recurrence
            );
        }

        return first;
    }

    // ==========================================
    // 4. CAP NHAT CUOC HOP
    // ==========================================

    @PutMapping("/{id}")
    @Transactional
    public Meeting update(
            @PathVariable String id,
            @RequestBody MeetingRequest request) {

        validate(request);

        Meeting meeting = findMeeting(id);

        List<String> participants =
                cleanParticipants(request.participants());

        String roomId = cleanRoom(request.room());

        checkSchedule(
                request.organizerId(),
                roomId,
                request.startTime(),
                request.endTime(),
                participants,
                id
        );

        meeting.setTitle(request.title().trim());
        meeting.setDescription(request.description());
        meeting.setOrganizerId(
                request.organizerId().trim()
        );
        meeting.setRoomId(roomId);
        meeting.setStartTime(request.startTime());
        meeting.setEndTime(request.endTime());
        meeting.setStatus("SCHEDULED");
        meeting.setCancelledAt(null);
        meeting.setCancelReason(null);
        meeting.setUpdatedAt(LocalDateTime.now());

        Meeting saved = meetings.save(meeting);

        syncParticipants(
                saved.getMeetingId(),
                participants
        );

        return saved;
    }

    // ==========================================
    // 5. HUY CUOC HOP
    // ==========================================

    @DeleteMapping("/{id}")
    @Transactional
    public void cancel(@PathVariable String id) {

        Meeting meeting = findMeeting(id);

        meeting.setStatus("CANCELLED");
        meeting.setCancelledAt(LocalDateTime.now());
        meeting.setUpdatedAt(LocalDateTime.now());

        meetings.save(meeting);

        // Can dong bo phan huy dat thiet bi
        // sau khi sua EquipmentBookingRepository
        // theo meetingId String.
    }

    // ==========================================
    // 6. GOI Y THOI GIAN HOP
    // ==========================================

    @PostMapping("/suggestions")
    public List<TimeSuggestion> suggestions(
            @RequestBody SuggestionRequest request) {

        if (request.date() == null
                || request.durationMinutes() == null
                || request.durationMinutes() < 15) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid date or duration"
            );
        }

        List<String> participants =
                cleanParticipants(request.participants());

        List<TimeSuggestion> available =
                new ArrayList<>();

        for (int hour = 9;
             hour < 17 && available.size() < 5;
             hour++) {

            for (int minute : List.of(0, 30)) {

                LocalDateTime start =
                        request.date().atTime(hour, minute);

                LocalDateTime end =
                        start.plusMinutes(
                                request.durationMinutes()
                        );

                if (start.isAfter(LocalDateTime.now())
                        && !hasParticipantConflict(
                                start,
                                end,
                                participants,
                                null
                        )) {

                    available.add(
                            new TimeSuggestion(start, end)
                    );
                }

                if (available.size() == 5) {
                    break;
                }
            }
        }

        return available;
    }

    // ==========================================
    // 7. TAO MOT PHIEN HOP
    // ==========================================

    private Meeting createOccurrence(
            MeetingRequest request,
            List<String> participants,
            String roomId,
            LocalDateTime start,
            LocalDateTime end,
            String seriesId,
            String recurrence) {

        checkSchedule(
                request.organizerId(),
                roomId,
                start,
                end,
                participants,
                null
        );

        LocalDateTime now = LocalDateTime.now();

        Meeting meeting = new Meeting();

        meeting.setMeetingId(
                UUID.randomUUID().toString()
        );

        meeting.setTitle(request.title().trim());
        meeting.setDescription(request.description());
        meeting.setRoomId(roomId);
        meeting.setOrganizerId(
                request.organizerId().trim()
        );

        meeting.setStartTime(start);
        meeting.setEndTime(end);
        meeting.setStatus("SCHEDULED");

        meeting.setIsRecurring(seriesId != null);
        meeting.setRecurrenceSeriesId(seriesId);

        meeting.setRecurrenceRule(
                seriesId == null ? null : recurrence
        );

        meeting.setCancelReason(null);
        meeting.setCancelledAt(null);

        meeting.setCreatedAt(now);
        meeting.setUpdatedAt(now);

        Meeting saved = meetings.save(meeting);

        syncParticipants(
                saved.getMeetingId(),
                participants
        );

        return saved;
    }

    // ==========================================
    // 8. KIEM TRA THONG TIN DAU VAO
    // ==========================================

    private void validate(MeetingRequest request) {

        if (request == null
                || request.title() == null
                || request.title().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Meeting title is required"
            );
        }

        if (request.startTime() == null
                || request.endTime() == null
                || !request.endTime()
                        .isAfter(request.startTime())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid meeting time"
            );
        }

        if (request.organizerId() == null
                || request.organizerId().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Organizer ID is required"
            );
        }

        Integer count = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM `user`
                WHERE user_id = :userId
                """,
                new MapSqlParameterSource(
                        "userId",
                        request.organizerId().trim()
                ),
                Integer.class
        );

        if (count == null || count == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Organizer not found"
            );
        }
    }

    // ==========================================
    // 9. KIEM TRA QUYEN US21 VA TRUNG LICH
    // ==========================================

    private void checkSchedule(
            String organizerId,
            String roomId,
            LocalDateTime start,
            LocalDateTime end,
            List<String> participants,
            String ignoredId) {

        // Kiem tra quyen truoc khi dat phong
        if (roomId != null) {

            permissionService.assertCanBook(
                    organizerId,
                    roomId
            );

            long conflicts = meetings.countRoomConflicts(
                    roomId,
                    ignoredId,
                    start,
                    end
            );

            if (conflicts > 0) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Room already booked"
                );
            }
        }

        if (hasParticipantConflict(
                start,
                end,
                participants,
                ignoredId)) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Participant already has a meeting"
            );
        }
    }

    // ==========================================
    // 10. KIEM TRA TRUNG LICH NGUOI THAM DU
    // ==========================================

    private boolean hasParticipantConflict(
            LocalDateTime start,
            LocalDateTime end,
            List<String> participants,
            String ignoredId) {

        if (participants.isEmpty()) {
            return false;
        }

        String sql = """
                SELECT COUNT(*)
                FROM meeting_participant mp
                JOIN meeting m
                    ON m.meeting_id = mp.meeting_id
                WHERE mp.user_id IN (:userIds)
                  AND m.status <> 'CANCELLED'
                  AND m.start_time < :endTime
                  AND m.end_time > :startTime
                """;

        MapSqlParameterSource params =
                new MapSqlParameterSource()
                        .addValue("userIds", participants)
                        .addValue("startTime", start)
                        .addValue("endTime", end);

        if (ignoredId != null) {
            sql += " AND m.meeting_id <> :ignoredId";
            params.addValue("ignoredId", ignoredId);
        }

        Long count = jdbc.queryForObject(
                sql,
                params,
                Long.class
        );

        return count != null && count > 0;
    }

    // ==========================================
    // 11. LUU NGUOI THAM DU THEO ERD
    // ==========================================

    private void syncParticipants(
            String meetingId,
            List<String> participants) {

        List<String> existing = jdbc.queryForList(
                """
                SELECT user_id
                FROM meeting_participant
                WHERE meeting_id = :meetingId
                """,
                new MapSqlParameterSource(
                        "meetingId", meetingId
                ),
                String.class
        );

        Set<String> desired = new HashSet<>(participants);
        Set<String> current = new HashSet<>(existing);

        // Xoa nguoi khong con tham du
        for (String userId : current) {

            if (!desired.contains(userId)) {

                jdbc.update(
                        """
                        DELETE FROM meeting_participant
                        WHERE meeting_id = :meetingId
                          AND user_id = :userId
                        """,
                        new MapSqlParameterSource()
                                .addValue("meetingId", meetingId)
                                .addValue("userId", userId)
                );
            }
        }

        // Them nguoi tham du moi
        for (String userId : desired) {

            if (!current.contains(userId)) {

                jdbc.update(
                        """
                        INSERT INTO meeting_participant
                        (
                            participant_id,
                            meeting_id,
                            user_id,
                            status,
                            responded_at
                        )
                        VALUES
                        (
                            :participantId,
                            :meetingId,
                            :userId,
                            'PENDING',
                            NULL
                        )
                        """,
                        new MapSqlParameterSource()
                                .addValue(
                                        "participantId",
                                        UUID.randomUUID().toString()
                                )
                                .addValue("meetingId", meetingId)
                                .addValue("userId", userId)
                );
            }
        }
    }

    // ==========================================
    // 12. CAC HAM HO TRO
    // ==========================================

    private Meeting findMeeting(String meetingId) {

        return meetings.findById(meetingId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Meeting not found"
                        )
                );
    }

    private List<String> cleanParticipants(
            List<String> participants) {

        if (participants == null) {
            return new ArrayList<>();
        }

        return participants.stream()
                .filter(value ->
                        value != null && !value.isBlank()
                )
                .map(String::trim)
                .distinct()
                .toList();
    }

    private String cleanRoom(String room) {

        return room == null || room.isBlank()
                ? null
                : room.trim();
    }

    // ==========================================
    // 13. REQUEST / RESPONSE
    // ==========================================

    public record MeetingRequest(
            String title,
            String description,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String organizerId,
            List<String> participants,
            String recurrence,
            Integer repeatCount,
            String room
    ) {
    }

    public record SuggestionRequest(
            LocalDate date,
            Integer durationMinutes,
            List<String> participants
    ) {
    }

    public record TimeSuggestion(
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {
    }
}
