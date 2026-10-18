package com.ttcs.meetingmanagement.controller;

import com.ttcs.meetingmanagement.dto.CreateMeetingRequest;
import com.ttcs.meetingmanagement.dto.MeetingResponse;
import com.ttcs.meetingmanagement.equipment.Equipment;
import com.ttcs.meetingmanagement.equipment.EquipmentRepository;
import com.ttcs.meetingmanagement.model.*;
import com.ttcs.meetingmanagement.repository.*;
import com.ttcs.meetingmanagement.room.Room;
import com.ttcs.meetingmanagement.room.RoomRepository;
import com.ttcs.meetingmanagement.service.AuthorizationService;
import com.ttcs.meetingmanagement.service.MeetingService;
import com.ttcs.meetingmanagement.service.RoomBookingPermissionService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@RestController
@RequestMapping("/api/meetings")
public class MeetingController {

    private final MeetingRepository meetings;
    private final EquipmentBookingRepository equipmentBookings;
    private final MeetingService meetingService;
    private final RoomRepository roomRepository;
    private final MeetingParticipantRepository participantRepository;
    private final MeetingEquipmentRepository meetingEquipmentRepository;
    private final EquipmentRepository equipmentRepository;
    private final UserRepository userRepository;
    private final AuthorizationService authorizationService;
    private final RoomBookingPermissionService permissionService;
    private final NamedParameterJdbcTemplate jdbc;

    public MeetingController(
            MeetingRepository meetings,
            EquipmentBookingRepository equipmentBookings,
            MeetingService meetingService,
            RoomRepository roomRepository,
            MeetingParticipantRepository participantRepository,
            MeetingEquipmentRepository meetingEquipmentRepository,
            EquipmentRepository equipmentRepository,
            UserRepository userRepository,
            AuthorizationService authorizationService,
            RoomBookingPermissionService permissionService,
            NamedParameterJdbcTemplate jdbc
    ) {
        this.meetings = meetings;
        this.equipmentBookings = equipmentBookings;
        this.meetingService = meetingService;
        this.roomRepository = roomRepository;
        this.participantRepository = participantRepository;
        this.meetingEquipmentRepository = meetingEquipmentRepository;
        this.equipmentRepository = equipmentRepository;
        this.userRepository = userRepository;
        this.authorizationService = authorizationService;
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

    public Meeting getById(@PathVariable String id) {
        return findMeeting(id);
    }

    // ==========================================
    // 2. LICH SU CUOC HOP
    // ==========================================

    @GetMapping("/history")
    public List<Meeting> history() {
        return meetings.findAllByEndTimeBeforeOrderByStartTimeDesc(LocalDateTime.now());
    }

    // ==========================================
    // 3. TAO CUOC HOP (US01 DTO API)
    // ==========================================

    @PostMapping("/create")
    public ResponseEntity<MeetingResponse> createMeeting(@Valid @RequestBody CreateMeetingRequest request) {
        MeetingResponse response = meetingService.createMeeting(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ==========================================
    // 4. TAO CUOC HOP (Develop / US03 API)
    // ==========================================

    @PostMapping
    @Transactional
    public Meeting create(
            @RequestBody MeetingRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestParam(value = "userId", required = false) String paramUserId
    ) {
        validate(request);

        String effectiveUserId = resolveUserId(headerUserId, paramUserId, request.organizerId(), "USER001");
        authorizationService.validateMeetingManagementPermission(effectiveUserId);

        List<String> participants = cleanParticipants(request.participants());

        int repeatCount = request.repeatCount() == null ? 0 : request.repeatCount();
        if (repeatCount < 0 || repeatCount > 12) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Repeat count must be between 0 and 12");
        }

        String recurrence = request.recurrence() == null ? "NONE" : request.recurrence().toUpperCase(Locale.ROOT);
        if (repeatCount > 0 && !List.of("WEEKLY", "MONTHLY").contains(recurrence)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid recurrence rule");
        }

        String rawRoom = cleanRoom(request.room());
        Room existingRoom = null;
        String resolvedRoomId = null;
        if (rawRoom != null) {
            existingRoom = roomRepository.findByRoomIdOrRoomNameFuzzy(rawRoom)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Phòng họp không tồn tại: " + rawRoom));
            resolvedRoomId = existingRoom.getRoomId();

            if (participants.size() > existingRoom.getCapacity()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số lượng người tham dự vượt quá sức chứa của phòng.");
            }

            if ("MAINTENANCE".equalsIgnoreCase(existingRoom.getStatus()) || "INACTIVE".equalsIgnoreCase(existingRoom.getStatus())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phòng đang bảo trì hoặc ngừng hoạt động.");
            }

            permissionService.assertCanBook(effectiveUserId, resolvedRoomId);

            long roomConflicts = meetings.countRoomConflicts(resolvedRoomId, null, request.startTime(), request.endTime());
            if (roomConflicts > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Room already booked");
            }
        }

        if (hasParticipantConflict(request.startTime(), request.endTime(), participants, null)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Participant already has a meeting");
        }

        String seriesId = repeatCount > 0 ? UUID.randomUUID().toString() : null;

        Meeting first = createOccurrence(
                request,
                participants,
                resolvedRoomId,
                request.startTime(),
                request.endTime(),
                seriesId,
                recurrence
        );

        if (existingRoom != null) {
            existingRoom.setStatus("OCCUPIED");
            roomRepository.save(existingRoom);
        }

        if (request.equipmentIds() != null && !request.equipmentIds().isEmpty()) {
            saveEquipments(first, request.equipmentIds());
        }

        Duration duration = Duration.between(request.startTime(), request.endTime());

        for (int i = 1; i <= repeatCount; i++) {
            LocalDateTime start = recurrence.equals("WEEKLY")
                    ? request.startTime().plusWeeks(i)
                    : request.startTime().plusMonths(i);
            LocalDateTime end = start.plus(duration);

            if (resolvedRoomId != null && meetings.countRoomConflicts(resolvedRoomId, null, start, end) > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Xung đột lịch phòng cho chuỗi lặp lại.");
            }
            if (hasParticipantConflict(start, end, participants, null)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Participant already has a meeting");
            }

            Meeting occ = createOccurrence(request, participants, resolvedRoomId, start, end, seriesId, recurrence);
            if (request.equipmentIds() != null && !request.equipmentIds().isEmpty()) {
                saveEquipments(occ, request.equipmentIds());
            }
        }

        return first;
    }

    public Meeting create(MeetingRequest request) {
        return create(request, null, null);
    }

    // ==========================================
    // 5. CAP NHAT CUOC HOP (US02)
    // ==========================================

    @Transactional
    public Meeting update(
            @PathVariable String id,
            @RequestBody MeetingRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestParam(value = "userId", required = false) String paramUserId
    ) {
        validate(request);

        Meeting meeting = findMeeting(id);

        String effectiveUserId = resolveUserId(headerUserId, paramUserId, request.organizerId(), meeting.getOrganizerId());
        authorizationService.validateMeetingManagementPermission(effectiveUserId);

        if ("CANCELLED".equalsIgnoreCase(meeting.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không thể cập nhật cuộc họp đã bị hủy.");
        }

        List<String> participants = cleanParticipants(request.participants());
        String newRoom = cleanRoom(request.room());
        String oldRoom = meeting.getRoomId();
        Room existingRoom = null;
        String resolvedNewRoomId = null;

        if (newRoom != null) {
            existingRoom = roomRepository.findByRoomIdOrRoomNameFuzzy(newRoom)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Phòng họp không tồn tại: " + newRoom));
            resolvedNewRoomId = existingRoom.getRoomId();
            if (participants.size() > existingRoom.getCapacity()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số lượng người tham dự vượt quá sức chứa của phòng.");
            }
            permissionService.assertCanBook(effectiveUserId, resolvedNewRoomId);
            long roomConflicts = meetings.countRoomConflicts(resolvedNewRoomId, id, request.startTime(), request.endTime());
            if (roomConflicts > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Phòng họp đã được đặt trong khoảng thời gian này.");
            }
            existingRoom.setStatus("OCCUPIED");
            roomRepository.save(existingRoom);
        }

        if (oldRoom != null && !oldRoom.equalsIgnoreCase(resolvedNewRoomId)) {
            releaseRoomIfNoActiveMeetings(oldRoom, id);
        }

        if (hasParticipantConflict(request.startTime(), request.endTime(), participants, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Participant already has a meeting");
        }

        meeting.setTitle(request.title().trim());
        meeting.setDescription(request.description());
        meeting.setRoomId(resolvedNewRoomId);
        meeting.setStartTime(request.startTime());
        meeting.setEndTime(request.endTime());
        if (request.organizerId() != null && !request.organizerId().isBlank()) {
            meeting.setOrganizerId(request.organizerId().trim());
        }
        meeting.setStatus("SCHEDULED");
        meeting.setCancelledAt(null);
        meeting.setCancelReason(null);
        meeting.setUpdatedAt(LocalDateTime.now());

        Meeting saved = meetings.save(meeting);
        syncParticipants(saved.getMeetingId(), participants);

        if (resolvedNewRoomId != null) {
            for (EquipmentBooking booking : equipmentBookings.findAllByMeetingId(id)) {
                booking.setRoom(resolvedNewRoomId);
                booking.setStartTime(request.startTime().atZone(ZoneId.systemDefault()).toOffsetDateTime());
                booking.setEndTime(request.endTime().atZone(ZoneId.systemDefault()).toOffsetDateTime());
                equipmentBookings.save(booking);
            }
        }

        return saved;
    }

    public Meeting update(String id, MeetingRequest request) {
        return update(id, request, null, null);
    }

    // ==========================================
    // 6. HUY CUOC HOP
    // ==========================================

    @Transactional
    public void cancel(
            @PathVariable String id,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestParam(value = "userId", required = false) String paramUserId
    ) {
        Meeting meeting = findMeeting(id);

        String effectiveUserId = resolveUserId(headerUserId, paramUserId, null, meeting.getOrganizerId());
        authorizationService.validateMeetingManagementPermission(effectiveUserId);

        String roomId = meeting.getRoomId();

        meeting.setStatus("CANCELLED");
        meeting.setCancelledAt(LocalDateTime.now());
        meeting.setCancelReason("Người dùng yêu cầu hủy");
        meeting.setRoomId(null);
        meeting.setUpdatedAt(LocalDateTime.now());
        meetings.save(meeting);

        if (roomId != null) {
            releaseRoomIfNoActiveMeetings(roomId, id);
        }

        List<MeetingEquipment> meetingEqList = meetingEquipmentRepository.findById_MeetingId(id);
        for (MeetingEquipment me : meetingEqList) {
            Equipment eq = equipmentRepository.findById(me.getEquipmentId()).orElse(null);
            if (eq != null) {
                eq.setStatus("AVAILABLE");
                equipmentRepository.save(eq);
            }
        }

        for (EquipmentBooking booking : equipmentBookings.findAllByMeetingId(id)) {
            booking.setStatus(EquipmentBookingStatus.CANCELLED);
            equipmentBookings.save(booking);
            Equipment eq = booking.getEquipment();
            if (eq != null) {
                eq.setStatus("AVAILABLE");
                equipmentRepository.save(eq);
            }
        }
    }

    public void cancel(String id) {
        cancel(id, null, null);
    }

    // ==========================================
    // 7. GOI Y THOI GIAN HOP
    // ==========================================

    @PostMapping("/suggestions")
    public List<TimeSuggestion> suggestions(@RequestBody SuggestionRequest request) {
        if (request.date() == null || request.durationMinutes() == null || request.durationMinutes() < 15) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date or duration");
        }

        List<String> participants = cleanParticipants(request.participants());
        List<TimeSuggestion> available = new ArrayList<>();

        for (int hour = 9; hour < 17 && available.size() < 5; hour++) {
            for (int minute : List.of(0, 30)) {
                LocalDateTime start = request.date().atTime(hour, minute);
                LocalDateTime end = start.plusMinutes(request.durationMinutes());
                if (start.isAfter(LocalDateTime.now()) && !hasParticipantConflict(start, end, participants, null)) {
                    available.add(new TimeSuggestion(start, end));
                }
                if (available.size() == 5) {
                    break;
                }
            }
        }

        return available;
    }

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
        meeting.setMeetingId(UUID.randomUUID().toString());
        meeting.setTitle(request.title().trim());
        meeting.setDescription(request.description());
        meeting.setRoomId(roomId);
        meeting.setOrganizerId(request.organizerId().trim());
        meeting.setStartTime(start);
        meeting.setEndTime(end);
        meeting.setStatus("SCHEDULED");
        meeting.setIsRecurring(seriesId != null);
        meeting.setRecurrenceSeriesId(seriesId);
        meeting.setRecurrenceRule(seriesId == null ? null : recurrence);
        meeting.setCancelReason(null);
        meeting.setCancelledAt(null);
        meeting.setCreatedAt(now);
        meeting.setUpdatedAt(now);

        Meeting saved = meetings.save(meeting);
        syncParticipants(saved.getMeetingId(), participants);

        return saved;
    }

    private void validate(MeetingRequest request) {
        if (request == null || request.title() == null || request.title().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Meeting title is required");
        }
        if (request.startTime() == null || request.endTime() == null || !request.endTime().isAfter(request.startTime())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid meeting time");
        }
        if (request.organizerId() == null || request.organizerId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Organizer ID is required");
        }

        Integer count = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM `user`
                WHERE user_id = :userId
                """,
                new MapSqlParameterSource("userId", request.organizerId().trim()),
                Integer.class
        );

        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Organizer not found");
        }
        if (request.startTime().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không thể tạo cuộc họp trong quá khứ.");
        }
    }

    private void checkSchedule(
            String organizerId,
            String roomId,
            LocalDateTime start,
            LocalDateTime end,
            List<String> participants,
            String ignoredId) {

        if (roomId != null) {
            permissionService.assertCanBook(organizerId, roomId);

            long conflicts = meetings.countRoomConflicts(roomId, ignoredId, start, end);
            if (conflicts > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Room already booked");
            }
        }

        if (hasParticipantConflict(start, end, participants, ignoredId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Participant already has a meeting");
        }
    }

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

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userIds", participants)
                .addValue("startTime", start)
                .addValue("endTime", end);

        if (ignoredId != null) {
            sql += " AND m.meeting_id <> :ignoredId";
            params.addValue("ignoredId", ignoredId);
        }

        Long count = jdbc.queryForObject(sql, params, Long.class);
        return count != null && count > 0;
    }

    private void syncParticipants(String meetingId, List<String> participants) {
        List<String> existing = jdbc.queryForList(
                """
                SELECT user_id
                FROM meeting_participant
                WHERE meeting_id = :meetingId
                """,
                new MapSqlParameterSource("meetingId", meetingId),
                String.class
        );

        Set<String> desired = new HashSet<>(participants);
        Set<String> current = new HashSet<>(existing);

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

        for (String userId : desired) {
            if (!current.contains(userId)) {
                String validUserId = findOrCreateUser(userId);
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
                                .addValue("participantId", UUID.randomUUID().toString())
                                .addValue("meetingId", meetingId)
                                .addValue("userId", validUserId)
                );
            }
        }
    }

    private void saveEquipments(Meeting meeting, List<String> equipmentIds) {
        for (String eqId : equipmentIds) {
            if (eqId == null || eqId.isBlank()) continue;
            Equipment eq = equipmentRepository.findById(eqId.trim()).orElse(null);
            if (eq != null) {
                MeetingEquipment me = new MeetingEquipment(meeting.getMeetingId(), eq.getEquipmentId(), 1);
                meetingEquipmentRepository.save(me);

                eq.setStatus("IN_USE");
                equipmentRepository.save(eq);

                EquipmentBooking booking = new EquipmentBooking();
                booking.setEquipment(eq);
                booking.setMeetingId(meeting.getMeetingId());
                booking.setRoom(meeting.getRoomId() != null ? meeting.getRoomId() : "NONE");
                booking.setQuantity(1);
                booking.setStartTime(meeting.getStartTime().atZone(ZoneId.systemDefault()).toOffsetDateTime());
                booking.setEndTime(meeting.getEndTime().atZone(ZoneId.systemDefault()).toOffsetDateTime());
                booking.setStatus(EquipmentBookingStatus.BOOKED);
                equipmentBookings.save(booking);
            }
        }
    }

    private String findOrCreateUser(String identifier) {
        var userOpt = userRepository.findById(identifier);
        if (userOpt.isPresent()) {
            return userOpt.get().getUserId();
        }

        var userByEmailOpt = userRepository.findByEmail(identifier);
        if (userByEmailOpt.isPresent()) {
            return userByEmailOpt.get().getUserId();
        }

        User newUser = new User();
        String generatedUserId = identifier.contains("@") ? "USR_" + UUID.randomUUID().toString().substring(0, 8) : identifier;
        newUser.setUserId(generatedUserId);
        newUser.setEmail(identifier.contains("@") ? identifier : identifier + "@company.com");
        newUser.setFullName(identifier.contains("@") ? identifier.substring(0, identifier.indexOf("@")) : identifier);
        newUser.setStatus("ACTIVE");
        newUser.setCreatedAt(LocalDateTime.now());
        newUser.setUpdatedAt(LocalDateTime.now());
        return userRepository.save(newUser).getUserId();
    }

    private Meeting findMeeting(String meetingId) {
        return meetings.findById(meetingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Meeting not found"));
    }

    private List<String> cleanParticipants(List<String> participants) {
        if (participants == null) {
            return new ArrayList<>();
        }
        return participants.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private String cleanRoom(String room) {
        return room == null || room.isBlank() ? null : room.trim();
    }

    private String resolveUserId(String headerUserId, String paramUserId, String requestUserId, String fallbackUserId) {
        if (headerUserId != null && !headerUserId.isBlank()) return headerUserId.trim();
        if (paramUserId != null && !paramUserId.isBlank()) return paramUserId.trim();
        if (requestUserId != null && !requestUserId.isBlank()) return requestUserId.trim();
        if (fallbackUserId != null && !fallbackUserId.isBlank()) return fallbackUserId.trim();
        return "USER001";
    }

    private void releaseRoomIfNoActiveMeetings(String roomId, String excludedMeetingId) {
        long activeCount = meetings.findAll().stream()
                .filter(m -> !m.getMeetingId().equals(excludedMeetingId))
                .filter(m -> roomId.equalsIgnoreCase(m.getRoomId()))
                .filter(m -> "SCHEDULED".equalsIgnoreCase(m.getStatus()) || "IN_PROGRESS".equalsIgnoreCase(m.getStatus()))
                .count();
        if (activeCount == 0) {
            roomRepository.findById(roomId).ifPresent(r -> {
                r.setStatus("AVAILABLE");
                roomRepository.save(r);
            });
        }
    }

    public record MeetingRequest(
            String title,
            String description,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String organizerId,
            List<String> participants,
            String recurrence,
            Integer repeatCount,
            String room,
            List<String> equipmentIds
    ) {
        public MeetingRequest(
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
            this(title, description, startTime, endTime, organizerId, participants, recurrence, repeatCount, room, null);
        }
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
