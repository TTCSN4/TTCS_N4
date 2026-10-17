package com.ttcs.meetingmanagement.service;

import com.ttcs.meetingmanagement.dto.CreateMeetingRequest;
import com.ttcs.meetingmanagement.dto.MeetingResponse;
import com.ttcs.meetingmanagement.equipment.Equipment;
import com.ttcs.meetingmanagement.equipment.EquipmentRepository;
import com.ttcs.meetingmanagement.exception.MeetingException;
import com.ttcs.meetingmanagement.model.*;
import com.ttcs.meetingmanagement.repository.*;
import com.ttcs.meetingmanagement.room.Room;
import com.ttcs.meetingmanagement.room.RoomRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class MeetingService {

    private final MeetingRepository meetingRepository;
    private final RoomRepository roomRepository;
    private final EquipmentRepository equipmentRepository;
    private final MeetingParticipantRepository participantRepository;
    private final MeetingEquipmentRepository meetingEquipmentRepository;
    private final UserRepository userRepository;
    private final EquipmentBookingRepository equipmentBookingRepository;
    private final AuthorizationService authorizationService;

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
        this.meetingRepository = meetingRepository;
        this.roomRepository = roomRepository;
        this.equipmentRepository = equipmentRepository;
        this.participantRepository = participantRepository;
        this.meetingEquipmentRepository = meetingEquipmentRepository;
        this.userRepository = userRepository;
        this.equipmentBookingRepository = equipmentBookingRepository;
        this.authorizationService = authorizationService;
    }

    @Transactional
    public MeetingResponse createMeeting(CreateMeetingRequest request) {

        // 1. Kiểm tra thời gian
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

        // 2. Kiểm tra organizer có tồn tại trong bảng user
        if (request.getOrganizerId() == null || request.getOrganizerId().isBlank()) {
            throw new MeetingException(
                    "Organizer ID không được để trống",
                    HttpStatus.BAD_REQUEST
            );
        }

        String organizerId = request.getOrganizerId().trim();
        if (meetingRepository.countUserById(organizerId) == 0) {
            throw new MeetingException(
                    "Organizer không tồn tại",
                    HttpStatus.NOT_FOUND
            );
        }

        // Xác thực phân quyền: Chỉ Admin và Project Manager mới được tạo lịch họp
        authorizationService.validateMeetingManagementForService(organizerId);

        // 3. Nếu có roomId thì kiểm tra phòng
        Room room = null;
        if (request.getRoomId() != null && !request.getRoomId().isBlank()) {
            String roomId = request.getRoomId().trim();

            room = roomRepository.findByRoomIdOrRoomNameFuzzy(roomId).orElseThrow(() ->
                    new MeetingException("Phòng họp không tồn tại: " + roomId, HttpStatus.NOT_FOUND));

            // Kiểm tra sức chứa nếu có danh sách người tham gia
            if (request.getParticipants() != null && !request.getParticipants().isEmpty()) {
                int participantCount = request.getParticipants().size();
                if (participantCount > room.getCapacity()) {
                    throw new MeetingException(
                            "Số lượng người tham dự (" + participantCount + ") vượt quá sức chứa của phòng (" + room.getCapacity() + ")",
                            HttpStatus.BAD_REQUEST
                    );
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

        // 5. Kiểm tra thiết bị yêu cầu (nếu có)
        List<Equipment> equipmentsToBook = new ArrayList<>();
        if (request.getEquipmentIds() != null && !request.getEquipmentIds().isEmpty()) {
            for (String eqId : request.getEquipmentIds()) {
                if (eqId == null || eqId.isBlank()) continue;
                Equipment eq = equipmentRepository.findById(eqId.trim()).orElseThrow(() ->
                        new MeetingException("Không tìm thấy thiết bị: " + eqId, HttpStatus.NOT_FOUND));

                if ("BROKEN".equalsIgnoreCase(eq.getStatus()) || "MAINTENANCE".equalsIgnoreCase(eq.getStatus())) {
                    throw new MeetingException(
                            "Thiết bị " + eq.getEquipmentName() + " (" + eqId + ") không khả dụng để đặt (đang hỏng hoặc bảo trì)",
                            HttpStatus.BAD_REQUEST
                    );
                }
                equipmentsToBook.add(eq);
            }
        }

        // 6. Kiểm tra lịch lặp
        boolean recurring = Boolean.TRUE.equals(request.getIsRecurring());

        if (recurring && (request.getRecurrenceRule() == null || request.getRecurrenceRule().isBlank())) {
            throw new MeetingException(
                    "Recurrence rule không được để trống khi tạo lịch lặp",
                    HttpStatus.BAD_REQUEST
            );
        }

        // 7. Tạo đối tượng Meeting theo ERD
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

        // Lưu cuộc họp vào database
        Meeting saved = meetingRepository.save(meeting);

        // 8. CẬP NHẬT TRẠNG THÁI PHÒNG TRONG DATABASE KHI ĐẶT THÀNH CÔNG
        if (room != null) {
            room.setStatus("OCCUPIED");
            roomRepository.save(room);
        }

        // 9. LƯU NGƯỜI THAM DỰ VÀO BẢNG meeting_participant
        List<String> savedParticipantUserIds = new ArrayList<>();
        // Lưu organizer với trạng thái ACCEPTED
        MeetingParticipant hostParticipant = new MeetingParticipant(saved.getMeetingId(), organizerId);
        hostParticipant.setStatus("ACCEPTED");
        hostParticipant.setRespondedAt(now);
        participantRepository.save(hostParticipant);
        savedParticipantUserIds.add(organizerId);

        if (request.getParticipants() != null) {
            for (String rawUser : request.getParticipants()) {
                if (rawUser == null || rawUser.isBlank()) continue;
                String cleanUser = rawUser.trim();
                if (cleanUser.equalsIgnoreCase(organizerId)) continue;

                String validUserId = findOrCreateUser(cleanUser);
                MeetingParticipant mp = new MeetingParticipant(saved.getMeetingId(), validUserId);
                mp.setStatus("INVITED");
                participantRepository.save(mp);
                savedParticipantUserIds.add(validUserId);
            }
        }

        // 10. LƯU THIẾT BỊ VÀO BẢNG meeting_equipment VÀ CẬP NHẬT TRẠNG THÁI THIẾT BỊ TRONG DATABASE
        List<String> savedEquipmentIds = new ArrayList<>();
        for (Equipment eq : equipmentsToBook) {
            // Lưu vào bảng meeting_equipment
            MeetingEquipment me = new MeetingEquipment(saved.getMeetingId(), eq.getEquipmentId(), 1);
            meetingEquipmentRepository.save(me);

            // Cập nhật trạng thái thiết bị thành IN_USE trong database
            eq.setStatus("IN_USE");
            equipmentRepository.save(eq);
            savedEquipmentIds.add(eq.getEquipmentId());

            // Lưu bản ghi EquipmentBooking để tương thích lịch đặt thiết bị
            EquipmentBooking booking = new EquipmentBooking();
            booking.setEquipment(eq);
            booking.setMeetingId(saved.getMeetingId());
            booking.setRoom(saved.getRoomId());
            booking.setQuantity(1);
            booking.setStartTime(saved.getStartTime().atZone(ZoneId.systemDefault()).toOffsetDateTime());
            booking.setEndTime(saved.getEndTime().atZone(ZoneId.systemDefault()).toOffsetDateTime());
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

    private String findOrCreateUser(String identifier) {
        // Kiểm tra theo userId
        var userOpt = userRepository.findById(identifier);
        if (userOpt.isPresent()) {
            return userOpt.get().getUserId();
        }

        // Kiểm tra theo email
        var userByEmailOpt = userRepository.findByEmail(identifier);
        if (userByEmailOpt.isPresent()) {
            return userByEmailOpt.get().getUserId();
        }

        // Nếu chưa có, tạo mới User để thỏa mãn khóa ngoại fk_participant_user
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
}