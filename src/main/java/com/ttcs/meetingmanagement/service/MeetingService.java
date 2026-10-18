package com.ttcs.meetingmanagement.service;

import com.ttcs.meetingmanagement.dto.CreateMeetingRequest;
import com.ttcs.meetingmanagement.dto.MeetingResponse;
import com.ttcs.meetingmanagement.equipment.Equipment;
import com.ttcs.meetingmanagement.equipment.EquipmentRepository;
import com.ttcs.meetingmanagement.exception.MeetingException;
import com.ttcs.meetingmanagement.model.*;
import com.ttcs.meetingmanagement.rbac.AuthenticatedUserIdentity;
import com.ttcs.meetingmanagement.repository.*;
import com.ttcs.meetingmanagement.room.Room;
import com.ttcs.meetingmanagement.room.RoomRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;

@Service
public class MeetingService {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final MeetingRepository meetingRepository;
    private final RoomRepository roomRepository;
    private final EquipmentRepository equipmentRepository;
    private final MeetingParticipantRepository participantRepository;
    private final MeetingEquipmentRepository meetingEquipmentRepository;
    private final UserRepository userRepository;
    private final EquipmentBookingRepository equipmentBookingRepository;
    private final AuthorizationService authorizationService;
    private final RoomBookingPermissionService permissionService;

    @org.springframework.beans.factory.annotation.Autowired
    public MeetingService(
            MeetingRepository meetingRepository,
            RoomRepository roomRepository,
            EquipmentRepository equipmentRepository,
            MeetingParticipantRepository participantRepository,
            MeetingEquipmentRepository meetingEquipmentRepository,
            UserRepository userRepository,
            EquipmentBookingRepository equipmentBookingRepository,
            AuthorizationService authorizationService,
            @org.springframework.lang.Nullable RoomBookingPermissionService permissionService
    ) {
        this.meetingRepository = meetingRepository;
        this.roomRepository = roomRepository;
        this.equipmentRepository = equipmentRepository;
        this.participantRepository = participantRepository;
        this.meetingEquipmentRepository = meetingEquipmentRepository;
        this.userRepository = userRepository;
        this.equipmentBookingRepository = equipmentBookingRepository;
        this.authorizationService = authorizationService;
        this.permissionService = permissionService;
    }

    public MeetingService(
            MeetingRepository meetingRepository,
            RoomRepository roomRepository,
            EquipmentRepository equipmentRepository,
            MeetingParticipantRepository participantRepository,
            MeetingEquipmentRepository meetingEquipmentRepository,
            UserRepository userRepository,
            EquipmentBookingRepository equipmentBookingRepository,
            AuthorizationService authorizationService
    ) {
        this(
                meetingRepository,
                roomRepository,
                equipmentRepository,
                participantRepository,
                meetingEquipmentRepository,
                userRepository,
                equipmentBookingRepository,
                authorizationService,
                null
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public MeetingResponse createMeeting(CreateMeetingRequest request) {

        // 1. Kiểm tra thời gian
        if (request == null) {
            throw new MeetingException("Dữ liệu yêu cầu không được để trống", HttpStatus.BAD_REQUEST);
        }

        if (request.getTitle() == null || request.getTitle().isBlank()) {
            throw new MeetingException("Tiêu đề cuộc họp không được để trống", HttpStatus.BAD_REQUEST);
        }

        if (request.getStartTime() == null || request.getEndTime() == null) {
            throw new MeetingException(
                    "Thời gian bắt đầu và kết thúc không được để trống",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new MeetingException(
                    "Thời gian kết thúc phải sau thời gian bắt đầu",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (request.getStartTime().isBefore(LocalDateTime.now())) {
            throw new MeetingException(
                    "Thời gian bắt đầu không được ở trong quá khứ",
                    HttpStatus.BAD_REQUEST
            );
        }

        // 2. Xác thực người tổ chức và kiểm tra phân quyền (FR01)
        String organizerId = null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            Object principal = auth.getPrincipal();
            if (principal instanceof Jwt jwt) {
                organizerId = jwt.getSubject();
            } else if (principal instanceof AuthenticatedUserIdentity identity) {
                organizerId = identity.getUserId();
            } else if (principal instanceof org.springframework.security.core.userdetails.UserDetails ud) {
                organizerId = ud.getUsername();
            } else if (principal instanceof String str && !"anonymousUser".equalsIgnoreCase(str)) {
                organizerId = str;
            }
        }

        if (organizerId == null || organizerId.isBlank()) {
            organizerId = request.getOrganizerId();
        }

        if (organizerId == null || organizerId.isBlank()) {
            throw new MeetingException("Vui lòng đăng nhập để tạo cuộc họp", HttpStatus.UNAUTHORIZED);
        }

        organizerId = organizerId.trim();
        request.setOrganizerId(organizerId);

        if (meetingRepository.countUserById(organizerId) == 0) {
            throw new MeetingException("Organizer không tồn tại", HttpStatus.NOT_FOUND);
        }

        // Kiểm tra vai trò thực tế từ database (ADMIN / PROJECT_MANAGER)
        authorizationService.validateMeetingManagementForService(organizerId);

        // 3. Nếu có roomId thì kiểm tra phòng (FR03)
        Room room = null;
        if (request.getRoomId() != null && !request.getRoomId().isBlank()) {
            String roomId = request.getRoomId().trim();

            Room resolvedRoom = roomRepository.findByRoomIdOrRoomNameFuzzy(roomId).orElseThrow(() ->
                    new MeetingException("Phòng họp không tồn tại: " + roomId, HttpStatus.NOT_FOUND));

            // Khóa dòng phòng trong database (pessimistic lock) để ngăn race condition (FR03)
            room = roomRepository.findLockedById(resolvedRoom.getRoomId()).orElse(resolvedRoom);

            // Không cho đặt phòng đang bảo trì hoặc ngừng hoạt động
            if ("MAINTENANCE".equalsIgnoreCase(room.getStatus()) || "INACTIVE".equalsIgnoreCase(room.getStatus())) {
                throw new MeetingException(
                        "Phòng đang bảo trì hoặc ngừng hoạt động: " + room.getRoomName(),
                        HttpStatus.BAD_REQUEST
                );
            }

            // Kiểm tra sức chứa phòng (tính cả người tổ chức)
            final String finalOrganizerId = organizerId;
            int totalAttendees = 1;
            if (request.getParticipants() != null) {
                totalAttendees = (int) request.getParticipants().stream()
                        .filter(p -> p != null && !p.isBlank() && !p.equalsIgnoreCase(finalOrganizerId))
                        .count() + 1;
            }
            if (room.getCapacity() != null && totalAttendees > room.getCapacity()) {
                throw new MeetingException(
                        "Số lượng người tham dự (" + totalAttendees + ") vượt quá sức chứa của phòng (" + room.getCapacity() + ")",
                        HttpStatus.BAD_REQUEST
                );
            }

            // Kiểm tra quyền hạn phòng (US21) nếu có
            if (permissionService != null) {
                try {
                    permissionService.assertCanBook(organizerId, room.getRoomId());
                } catch (org.springframework.web.server.ResponseStatusException ex) {
                    throw new MeetingException(ex.getReason(), HttpStatus.valueOf(ex.getStatusCode().value()));
                }
            }

            // 4. Kiểm tra phòng có bị trùng lịch
            long conflictCount = meetingRepository.countRoomConflicts(
                    room.getRoomId(),
                    request.getStartTime(),
                    request.getEndTime()
            );

            if (conflictCount > 0) {
                throw new MeetingException(
                        "Phòng họp đã được đặt trong khoảng thời gian này",
                        HttpStatus.CONFLICT
                );
            }
        }

        // 4. Kiểm tra danh sách người tham dự (FR02, FR05)
        List<String> validatedParticipantUserIds = new ArrayList<>();
        if (request.getParticipants() != null) {
            for (String rawUser : request.getParticipants()) {
                if (rawUser == null || rawUser.isBlank()) continue;
                String cleanUser = rawUser.trim();
                if (cleanUser.equalsIgnoreCase(organizerId)) continue;

                var userOpt = userRepository.findById(cleanUser)
                        .or(() -> userRepository.findByEmail(cleanUser));

                if (userOpt.isEmpty()) {
                    throw new MeetingException("Người tham dự không tồn tại: " + cleanUser, HttpStatus.NOT_FOUND);
                }
                validatedParticipantUserIds.add(userOpt.get().getUserId());
            }
        }

        // 5. Kiểm tra thiết bị và số lượng yêu cầu (FR04)
        Map<String, Integer> equipmentQuantities = new LinkedHashMap<>();
        if (request.getEquipmentIds() != null) {
            for (String eqId : request.getEquipmentIds()) {
                if (eqId == null || eqId.isBlank()) continue;
                String cleanId = eqId.trim();
                equipmentQuantities.put(cleanId, equipmentQuantities.getOrDefault(cleanId, 0) + 1);
            }
        }

        OffsetDateTime startOffset = request.getStartTime().atZone(VN_ZONE).toOffsetDateTime();
        OffsetDateTime endOffset = request.getEndTime().atZone(VN_ZONE).toOffsetDateTime();
        List<Equipment> equipmentsToBook = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : equipmentQuantities.entrySet()) {
            String eqId = entry.getKey();
            int requestedQuantity = entry.getValue();

            // Khóa dòng thiết bị để ngăn race condition (FR04)
            Equipment eq = equipmentRepository.findByIdForUpdate(eqId).orElseThrow(() ->
                    new MeetingException("Không tìm thấy thiết bị: " + eqId, HttpStatus.NOT_FOUND));

            if ("BROKEN".equalsIgnoreCase(eq.getStatus()) || "MAINTENANCE".equalsIgnoreCase(eq.getStatus())) {
                throw new MeetingException(
                        "Thiết bị " + eq.getEquipmentName() + " (" + eqId + ") không khả dụng để đặt (đang hỏng hoặc bảo trì)",
                        HttpStatus.BAD_REQUEST
                );
            }

            // Kiểm tra số lượng thiết bị khả dụng trong khoảng thời gian cuộc họp
            List<EquipmentBooking> overlaps = equipmentBookingRepository.findOverlappingBookings(
                    eq.getEquipmentId(),
                    List.of(EquipmentBookingStatus.BOOKED, EquipmentBookingStatus.MAINTENANCE),
                    startOffset,
                    endOffset
            );

            List<UsageEvent> events = new ArrayList<>();
            for (EquipmentBooking existing : overlaps) {
                OffsetDateTime clippedStart = existing.getStartTime().isBefore(startOffset) ? startOffset : existing.getStartTime();
                OffsetDateTime clippedEnd = existing.getEndTime().isAfter(endOffset) ? endOffset : existing.getEndTime();
                events.add(new UsageEvent(clippedStart, existing.getQuantity()));
                events.add(new UsageEvent(clippedEnd, -existing.getQuantity()));
            }
            events.add(new UsageEvent(startOffset, requestedQuantity));
            events.add(new UsageEvent(endOffset, -requestedQuantity));

            events.sort(Comparator.comparing(UsageEvent::time).thenComparingInt(UsageEvent::change));
            int inUse = 0;
            int peak = 0;
            for (UsageEvent event : events) {
                inUse += event.change();
                peak = Math.max(peak, inUse);
            }

            int totalQuantity = eq.getTotalQuantity() != null ? eq.getTotalQuantity() : 1;
            if (peak > totalQuantity) {
                throw new MeetingException(
                        "Thiết bị " + eq.getEquipmentName() + " (" + eqId + ") không đủ số lượng khả dụng hoặc đã bị trùng lịch trong khung giờ này",
                        HttpStatus.CONFLICT
                );
            }

            equipmentsToBook.add(eq);
        }

        // 6. Kiểm tra lịch lặp
        boolean recurring = Boolean.TRUE.equals(request.getIsRecurring());

        if (recurring && (request.getRecurrenceRule() == null || request.getRecurrenceRule().isBlank())) {
            throw new MeetingException(
                    "Recurrence rule không được để trống khi tạo lịch lặp",
                    HttpStatus.BAD_REQUEST
            );
        }

        // 7. Tạo và lưu đối tượng Meeting theo ERD (FR05)
        Meeting meeting = new Meeting();
        meeting.setMeetingId(UUID.randomUUID().toString());
        meeting.setOrganizerId(organizerId);
        meeting.setRoomId(room != null ? room.getRoomId() : null);
        meeting.setTitle(request.getTitle().trim());
        meeting.setDescription(request.getDescription());
        meeting.setStartTime(request.getStartTime());
        meeting.setEndTime(request.getEndTime());
        meeting.setStatus("SCHEDULED");
        meeting.setIsRecurring(recurring);

        if (recurring) {
            meeting.setRecurrenceRule(request.getRecurrenceRule().trim());
            meeting.setRecurrenceSeriesId(UUID.randomUUID().toString());
        } else {
            meeting.setRecurrenceRule(null);
            meeting.setRecurrenceSeriesId(null);
        }

        meeting.setCancelReason(null);
        meeting.setCancelledAt(null);

        LocalDateTime now = LocalDateTime.now();
        meeting.setCreatedAt(now);
        meeting.setUpdatedAt(now);

        Meeting saved = meetingRepository.save(meeting);

        // 8. CẬP NHẬT TRẠNG THÁI PHÒNG TRONG DATABASE KHI ĐẶT THÀNH CÔNG (FR05, FR06)
        if (room != null) {
            room.setStatus("OCCUPIED");
            roomRepository.save(room);
        }

        // 9. LƯU NGƯỜI THAM DỰ VÀO BẢNG meeting_participant (FR05)
        List<String> savedParticipantUserIds = new ArrayList<>();
        MeetingParticipant hostParticipant = new MeetingParticipant(saved.getMeetingId(), organizerId);
        hostParticipant.setStatus("ACCEPTED");
        hostParticipant.setRespondedAt(now);
        participantRepository.save(hostParticipant);
        savedParticipantUserIds.add(organizerId);

        for (String participantUserId : validatedParticipantUserIds) {
            MeetingParticipant mp = new MeetingParticipant(saved.getMeetingId(), participantUserId);
            mp.setStatus("INVITED");
            participantRepository.save(mp);
            savedParticipantUserIds.add(participantUserId);
        }

        // 10. LƯU THIẾT BỊ VÀO BẢNG meeting_equipment VÀ CẬP NHẬT TRẠNG THÁI THIẾT BỊ (FR04, FR05, FR06)
        List<String> savedEquipmentIds = new ArrayList<>();
        for (Equipment eq : equipmentsToBook) {
            int quantity = equipmentQuantities.getOrDefault(eq.getEquipmentId(), 1);

            // Lưu vào bảng meeting_equipment
            MeetingEquipment me = new MeetingEquipment(saved.getMeetingId(), eq.getEquipmentId(), quantity);
            meetingEquipmentRepository.save(me);

            // Cập nhật trạng thái thiết bị thành IN_USE trong database
            eq.setStatus("IN_USE");
            equipmentRepository.save(eq);
            savedEquipmentIds.add(eq.getEquipmentId());

            // Lưu bản ghi EquipmentBooking để tương thích lịch đặt thiết bị theo khung giờ
            EquipmentBooking booking = new EquipmentBooking();
            booking.setEquipment(eq);
            booking.setMeetingId(saved.getMeetingId());
            booking.setRoom(saved.getRoomId() != null ? saved.getRoomId() : "NONE");
            booking.setQuantity(quantity);
            booking.setStartTime(startOffset);
            booking.setEndTime(endOffset);
            booking.setStatus(EquipmentBookingStatus.BOOKED);
            equipmentBookingRepository.save(booking);
        }

        // 11. Trả response đầy đủ
        MeetingResponse response = toResponse(saved);
        if (room != null) {
            response.setRoomStatus(room.getStatus());
        }
        response.setParticipants(savedParticipantUserIds);
        response.setEquipmentIds(savedEquipmentIds);
        return response;
    }

    private MeetingResponse toResponse(Meeting meeting) {
        MeetingResponse response = new MeetingResponse();
        response.setMeetingId(meeting.getMeetingId());
        response.setOrganizerId(meeting.getOrganizerId());
        response.setRoomId(meeting.getRoomId());
        response.setTitle(meeting.getTitle());
        response.setDescription(meeting.getDescription());
        response.setStartTime(meeting.getStartTime());
        response.setEndTime(meeting.getEndTime());
        response.setStatus(meeting.getStatus());
        response.setIsRecurring(meeting.getIsRecurring());
        response.setRecurrenceRule(meeting.getRecurrenceRule());
        response.setRecurrenceSeriesId(meeting.getRecurrenceSeriesId());
        response.setCreatedAt(meeting.getCreatedAt());
        response.setUpdatedAt(meeting.getUpdatedAt());
        return response;
    }

    private record UsageEvent(OffsetDateTime time, int change) {
    }
}