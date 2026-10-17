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

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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

    public MeetingController(
            MeetingRepository meetings,
            EquipmentBookingRepository equipmentBookings,
            MeetingService meetingService,
            RoomRepository roomRepository,
            MeetingParticipantRepository participantRepository,
            MeetingEquipmentRepository meetingEquipmentRepository,
            EquipmentRepository equipmentRepository,
            UserRepository userRepository,
            AuthorizationService authorizationService
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
    }

    @GetMapping
    public List<Meeting> list() {
        return meetings.findAllByOrderByStartTimeAsc();
    }

    @GetMapping("/{id}")
    public Meeting getById(@PathVariable String id) {
        return findMeeting(id);
    }

    @GetMapping("/history")
    public List<Meeting> history() {
        return meetings.findAllByEndTimeBeforeOrderByStartTimeDesc(LocalDateTime.now());
    }

    // US01 - API tạo cuộc họp chuẩn DTO
    @PostMapping("/create")
    public ResponseEntity<MeetingResponse> createMeeting(@Valid @RequestBody CreateMeetingRequest request) {
        MeetingResponse response = meetingService.createMeeting(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping
    @Transactional
    public Meeting create(
            @RequestBody MeetingRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestParam(value = "userId", required = false) String paramUserId
    ) {
        validate(request);

        // Phân quyền: Chỉ Admin và Project Manager mới được tạo lịch họp
        String effectiveUserId = resolveUserId(headerUserId, paramUserId, request.organizerId(), "USER001");
        authorizationService.validateMeetingManagementPermission(effectiveUserId);

        List<String> participants = cleanParticipants(request.participants());

        String room = cleanRoom(request.room());
        Room existingRoom = null;
        if (room != null) {
            existingRoom = roomRepository.findByRoomIdOrRoomNameFuzzy(room)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Phòng họp không tồn tại: " + room));

            if (participants.size() > existingRoom.getCapacity()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số lượng người tham dự vượt quá sức chứa của phòng.");
            }

            long roomConflicts = meetings.countRoomConflicts(existingRoom.getRoomId(), null, request.startTime(), request.endTime());
            if (roomConflicts > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Phòng họp đã được đặt trong khoảng thời gian này.");
            }
        }

        ensureNoConflict(request.startTime(), request.endTime(), participants, null);

        String resolvedRoomId = existingRoom != null ? existingRoom.getRoomId() : null;

        // Lưu cuộc họp đầu tiên
        Meeting first = save(request, participants, null, null, resolvedRoomId);

        // Cập nhật trạng thái phòng khi đặt thành công
        if (existingRoom != null) {
            existingRoom.setStatus("OCCUPIED");
            roomRepository.save(existingRoom);
        }

        // Lưu người tham dự
        saveParticipants(first.getMeetingId(), first.getOrganizerId(), participants);

        // Lưu thiết bị và cập nhật trạng thái thiết bị
        if (request.equipmentIds() != null && !request.equipmentIds().isEmpty()) {
            saveEquipments(first, request.equipmentIds());
        }

        int repeatCount = Math.clamp(request.repeatCount() == null ? 0 : request.repeatCount(), 0, 12);
        String recurrence = request.recurrence() == null ? "NONE" : request.recurrence().toUpperCase(Locale.ROOT);
        if (repeatCount > 0 && !List.of("WEEKLY", "MONTHLY").contains(recurrence)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tần suất lặp không hợp lệ.");
        }

        if (repeatCount > 0) {
            first.setRecurrenceSeriesId(first.getMeetingId());
            first.setIsRecurring(true);
            first.setRecurrenceRule("FREQ=" + recurrence);
            meetings.save(first);
            for (int occurrence = 1; occurrence <= repeatCount; occurrence++) {
                LocalDateTime start = recurrence.equals("WEEKLY")
                        ? request.startTime().plusWeeks(occurrence)
                        : request.startTime().plusMonths(occurrence);
                long seconds = java.time.Duration.between(request.startTime(), request.endTime()).getSeconds();
                LocalDateTime end = start.plusSeconds(seconds);

                if (resolvedRoomId != null && meetings.countRoomConflicts(resolvedRoomId, null, start, end) > 0) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Xung đột lịch phòng cho chuỗi lặp lại.");
                }
                ensureNoConflict(start, end, participants, null);
                Meeting occMeeting = save(request, participants, start, first.getMeetingId(), resolvedRoomId);
                occMeeting.setIsRecurring(true);
                occMeeting.setRecurrenceRule("FREQ=" + recurrence);
                meetings.save(occMeeting);
                saveParticipants(occMeeting.getMeetingId(), occMeeting.getOrganizerId(), participants);

                if (request.equipmentIds() != null && !request.equipmentIds().isEmpty()) {
                    saveEquipments(occMeeting, request.equipmentIds());
                }
            }
        }
        return first;
    }

    @PutMapping("/{id}")
    @Transactional
    public Meeting update(
            @PathVariable String id,
            @RequestBody MeetingRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestParam(value = "userId", required = false) String paramUserId
    ) {
        validate(request);
        Meeting meeting = findMeeting(id);

        // Phân quyền: Chỉ Admin và Project Manager mới được sửa lịch họp
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
            long roomConflicts = meetings.countRoomConflicts(resolvedNewRoomId, id, request.startTime(), request.endTime());
            if (roomConflicts > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Phòng họp đã được đặt trong khoảng thời gian này.");
            }
            existingRoom.setStatus("OCCUPIED");
            roomRepository.save(existingRoom);
        }

        // Nếu phòng cũ khác phòng mới thì giải phóng phòng cũ
        if (oldRoom != null && !oldRoom.equalsIgnoreCase(resolvedNewRoomId)) {
            releaseRoomIfNoActiveMeetings(oldRoom, id);
        }

        ensureNoConflict(request.startTime(), request.endTime(), participants, id);

        meeting.setTitle(request.title().trim());
        meeting.setDescription(request.description());
        meeting.setRoomId(resolvedNewRoomId);
        meeting.setStartTime(request.startTime());
        meeting.setEndTime(request.endTime());
        if (request.organizerId() != null && !request.organizerId().isBlank()) {
            meeting.setOrganizerId(request.organizerId().trim());
        }
        meeting.setUpdatedAt(LocalDateTime.now());

        // Đồng bộ người tham gia
        saveParticipants(meeting.getMeetingId(), meeting.getOrganizerId(), participants);

        // Đồng bộ thiết bị nếu phòng hoặc giờ thay đổi
        if (newRoom != null) {
            for (EquipmentBooking booking : equipmentBookings.findAllByMeetingId(id)) {
                booking.setRoom(newRoom);
                booking.setStartTime(request.startTime().atZone(ZoneId.systemDefault()).toOffsetDateTime());
                booking.setEndTime(request.endTime().atZone(ZoneId.systemDefault()).toOffsetDateTime());
                equipmentBookings.save(booking);
            }
        }

        return meetings.save(meeting);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public void cancel(
            @PathVariable String id,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestParam(value = "userId", required = false) String paramUserId
    ) {
        Meeting meeting = findMeeting(id);

        // Phân quyền: Chỉ Admin và Project Manager mới được hủy lịch họp
        String effectiveUserId = resolveUserId(headerUserId, paramUserId, null, meeting.getOrganizerId());
        authorizationService.validateMeetingManagementPermission(effectiveUserId);

        String roomId = meeting.getRoomId();

        meeting.setStatus("CANCELLED");
        meeting.setCancelledAt(LocalDateTime.now());
        meeting.setCancelReason("Người dùng yêu cầu hủy");
        meeting.setRoomId(null);
        meeting.setUpdatedAt(LocalDateTime.now());
        meetings.save(meeting);

        // Giải phóng phòng họp trong database
        if (roomId != null) {
            releaseRoomIfNoActiveMeetings(roomId, id);
        }

        // Giải phóng thiết bị trong database
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

    @PostMapping("/suggestions")
    public List<TimeSuggestion> suggestions(@RequestBody SuggestionRequest request) {
        if (request.date() == null || request.durationMinutes() == null || request.durationMinutes() < 15) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chọn ngày và thời lượng ít nhất 15 phút.");
        }
        List<String> participants = cleanParticipants(request.participants());
        List<TimeSuggestion> available = new ArrayList<>();
        for (int hour = 9; hour < 17 && available.size() < 5; hour++) {
            for (int minute : List.of(0, 30)) {
                LocalDateTime start = request.date().atTime(hour, minute);
                LocalDateTime end = start.plusMinutes(request.durationMinutes());
                if (start.isAfter(LocalDateTime.now()) && !hasConflict(start, end, participants, null)) {
                    available.add(new TimeSuggestion(start, end));
                }
                if (available.size() == 5) break;
            }
        }
        return available;
    }

    private Meeting save(MeetingRequest request, List<String> participants, LocalDateTime start, String seriesId, String resolvedRoomId) {
        LocalDateTime now = LocalDateTime.now();
        Meeting meeting = new Meeting();
        meeting.setMeetingId(UUID.randomUUID().toString());
        meeting.setTitle(request.title().trim());
        meeting.setDescription(request.description());
        meeting.setRoomId(resolvedRoomId != null ? resolvedRoomId : cleanRoom(request.room()));
        meeting.setStartTime(start == null ? request.startTime() : start);

        long duration = java.time.Duration.between(request.startTime(), request.endTime()).getSeconds();
        meeting.setEndTime(start == null ? request.endTime() : start.plusSeconds(duration));

        meeting.setOrganizerId(request.organizerId() == null || request.organizerId().isBlank() ? "USER001" : request.organizerId().trim());
        meeting.setStatus("SCHEDULED");
        meeting.setRecurrenceSeriesId(seriesId);
        meeting.setCreatedAt(now);
        meeting.setUpdatedAt(now);
        return meetings.save(meeting);
    }

    private void saveParticipants(String meetingId, String organizerId, List<String> participants) {
        List<MeetingParticipant> existing = participantRepository.findByMeetingId(meetingId);
        if (!existing.isEmpty()) {
            participantRepository.deleteAll(existing);
            participantRepository.flush();
        }

        // Lưu organizer
        if (organizerId != null && !organizerId.isBlank()) {
            String validOrgId = findOrCreateUser(organizerId.trim());
            MeetingParticipant host = new MeetingParticipant(meetingId, validOrgId);
            host.setStatus("ACCEPTED");
            host.setRespondedAt(LocalDateTime.now());
            participantRepository.save(host);
        }

        // Lưu người tham gia
        for (String user : participants) {
            if (user.equalsIgnoreCase(organizerId)) continue;
            String validUserId = findOrCreateUser(user);
            MeetingParticipant mp = new MeetingParticipant(meetingId, validUserId);
            mp.setStatus("INVITED");
            participantRepository.save(mp);
        }
        participantRepository.flush();
    }

    private void saveEquipments(Meeting meeting, List<String> equipmentIds) {
        for (String eqId : equipmentIds) {
            if (eqId == null || eqId.isBlank()) continue;
            Equipment eq = equipmentRepository.findById(eqId.trim()).orElse(null);
            if (eq != null) {
                // Lưu vào bảng meeting_equipment
                MeetingEquipment me = new MeetingEquipment(meeting.getMeetingId(), eq.getEquipmentId(), 1);
                meetingEquipmentRepository.save(me);

                // Cập nhật trạng thái thiết bị thành IN_USE
                eq.setStatus("IN_USE");
                equipmentRepository.save(eq);

                // Lưu EquipmentBooking
                EquipmentBooking booking = new EquipmentBooking();
                booking.setEquipment(eq);
                booking.setMeetingId(meeting.getMeetingId());
                booking.setRoom(meeting.getRoomId());
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

    private void validate(MeetingRequest request) {
        if (request.title() == null || request.title().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng nhập tiêu đề cuộc họp.");
        }
        if (request.startTime() == null || request.endTime() == null || !request.endTime().isAfter(request.startTime())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thời gian kết thúc phải sau thời gian bắt đầu.");
        }
        if (request.startTime().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không thể tạo cuộc họp trong quá khứ.");
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

    private void ensureNoConflict(LocalDateTime start, LocalDateTime end, List<String> participants, String ignoredId) {
        if (hasConflict(start, end, participants, ignoredId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Người tham dự đã có lịch trong khoảng thời gian này.");
        }
    }

    private boolean hasConflict(LocalDateTime start, LocalDateTime end, List<String> participants, String ignoredId) {
        if (participants.isEmpty()) return false;
        return meetings.findAll().stream()
                .filter(meeting -> !meeting.getMeetingId().equals(ignoredId))
                .filter(meeting -> "SCHEDULED".equalsIgnoreCase(meeting.getStatus()))
                .filter(meeting -> meeting.getStartTime().isBefore(end) && meeting.getEndTime().isAfter(start))
                .anyMatch(meeting -> {
                    List<MeetingParticipant> parts = participantRepository.findByMeetingId(meeting.getMeetingId());
                    return parts.stream().anyMatch(p -> participants.contains(p.getUserId()));
                });
    }

    private Meeting findMeeting(String id) {
        return meetings.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy cuộc họp."));
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
    ) { }

    public record SuggestionRequest(LocalDate date, Integer durationMinutes, List<String> participants) { }
    public record TimeSuggestion(LocalDateTime startTime, LocalDateTime endTime) { }
}