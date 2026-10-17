package com.ttcs.meetingmanagement;

import com.ttcs.meetingmanagement.equipment.Equipment;
import com.ttcs.meetingmanagement.equipment.EquipmentController;
import com.ttcs.meetingmanagement.equipment.EquipmentRepository;
import com.ttcs.meetingmanagement.model.EquipmentAvailabilityStatus;
import com.ttcs.meetingmanagement.model.EquipmentBooking;
import com.ttcs.meetingmanagement.model.EquipmentBookingStatus;
import com.ttcs.meetingmanagement.model.Meeting;
import com.ttcs.meetingmanagement.model.MeetingParticipant;
import com.ttcs.meetingmanagement.model.Role;
import com.ttcs.meetingmanagement.model.User;
import com.ttcs.meetingmanagement.controller.MeetingController;
import com.ttcs.meetingmanagement.repository.EquipmentBookingRepository;
import com.ttcs.meetingmanagement.repository.MeetingParticipantRepository;
import com.ttcs.meetingmanagement.repository.MeetingRepository;
import com.ttcs.meetingmanagement.repository.UserRepository;
import com.ttcs.meetingmanagement.room.Room;
import com.ttcs.meetingmanagement.room.RoomRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class MeetingmanagementApplicationTests {

    @Autowired
    private EquipmentController equipmentController;

    @Autowired
    private EquipmentRepository equipmentRepository;

    @Autowired
    private EquipmentBookingRepository bookingRepository;

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private MeetingParticipantRepository participantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private com.ttcs.meetingmanagement.service.MeetingService meetingService;

    @Autowired
    private com.ttcs.meetingmanagement.repository.MeetingEquipmentRepository meetingEquipmentRepository;

    @Autowired
    private com.ttcs.meetingmanagement.repository.RoleRepository roleRepository;

    @Autowired
    private com.ttcs.meetingmanagement.controller.MeetingController meetingController;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @Transactional
    void meetingQueriesFetchParticipantsBeforeSerialization() {
        // Đảm bảo User hợp lệ theo FK ERD
        User organizer = getOrCreateUser("USER_ORG_TEST", "Organizer Test", "org@example.com");
        User attendee = getOrCreateUser("USER_ATT_TEST", "Attendee Test", "attendee@example.com");

        LocalDateTime start = LocalDateTime.parse("2030-07-10T09:00:00");
        Meeting meeting = new Meeting();
        meeting.setTitle("Meeting with participants");
        meeting.setStartTime(start);
        meeting.setEndTime(start.plusHours(1));
        meeting.setOrganizerId(organizer.getUserId());
        meeting.setStatus("SCHEDULED");
        meeting.setCreatedAt(start.minusDays(1));
        meeting.setUpdatedAt(start.minusDays(1));
        Meeting saved = meetingRepository.saveAndFlush(meeting);

        MeetingParticipant participant = new MeetingParticipant(saved.getMeetingId(), attendee.getUserId());
        participantRepository.saveAndFlush(participant);
        entityManager.clear();

        Meeting loaded = meetingRepository.findAllByOrderByStartTimeAsc().stream()
                .filter(item -> item.getMeetingId().equals(saved.getMeetingId()))
                .findFirst()
                .orElseThrow();
        assertNotNull(loaded);
        List<MeetingParticipant> participants = participantRepository.findByMeetingId(loaded.getMeetingId());
        assertEquals(1, participants.size());
        assertEquals(attendee.getUserId(), participants.get(0).getUserId());
    }

    @Test
    @Transactional
    void statusAtTimeReportsAvailableBookedAndMaintenanceQuantities() {
        String roomId = "ROOM_TEST_STATUS";
        getOrCreateRoom(roomId, "Phòng Test Status", 20);

        Equipment equipment = saveEquipment(roomId, "Test projector", 3);

        OffsetDateTime start = OffsetDateTime.parse("2030-05-10T09:00:00+07:00");
        saveReservation(equipment, roomId, 1, start, start.plusHours(1), EquipmentBookingStatus.BOOKED);
        saveReservation(equipment, roomId, 1, start, start.plusHours(1), EquipmentBookingStatus.MAINTENANCE);

        EquipmentController.EquipmentStatusResponse during = equipmentController
                .status(roomId, start.plusMinutes(30))
                .get(0);
        assertEquals(3, during.totalQuantity());
        assertEquals(1, during.bookedQuantity());
        assertEquals(1, during.maintenanceQuantity());
        assertEquals(1, during.availableQuantity());
        assertEquals(List.of(
                EquipmentAvailabilityStatus.AVAILABLE,
                EquipmentAvailabilityStatus.BOOKED,
                EquipmentAvailabilityStatus.MAINTENANCE
        ), during.statuses());

        EquipmentController.EquipmentStatusResponse after = equipmentController
                .status(roomId, start.plusHours(1))
                .get(0);
        assertEquals(3, after.totalQuantity());
        assertEquals(3, after.availableQuantity());
        assertTrue(after.statuses().contains(EquipmentAvailabilityStatus.AVAILABLE));
        assertEquals(1, after.statuses().size());
    }

    @Test
    @Transactional
    void bookingRejectsOverlappingRequestsBeyondInventory() {
        String roomId = "ROOM_TEST_BOOK";
        getOrCreateRoom(roomId, "Phòng Test Booking", 15);

        Equipment equipment = saveEquipment(roomId, "One projector", 1);
        OffsetDateTime start = OffsetDateTime.parse("2030-06-10T09:00:00+07:00");
        saveReservation(equipment, roomId, 1, start, start.plusHours(1), EquipmentBookingStatus.BOOKED);

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () ->
                equipmentController.book(new EquipmentController.EquipmentBookingRequest(
                        equipment.getEquipmentId(),
                        null,
                        roomId,
                        1,
                        start.plusMinutes(15),
                        start.plusMinutes(45)
                ))
        );
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
    }

    @Test
    @Transactional
    void testCreateMeetingFlowSavesRoomEquipmentParticipantsAndUpdatesStatuses() {
        // Chuẩn bị dữ liệu mẫu
        String roomId = "ROOM_INTEGRATION_TEST";
        Room room = getOrCreateRoom(roomId, "Phòng Họp Test Status", 10);
        room.setStatus("AVAILABLE");
        roomRepository.saveAndFlush(room);

        Equipment eq = saveEquipment(roomId, "Máy chiếu 4K Test", 2);
        eq.setStatus("AVAILABLE");
        equipmentRepository.saveAndFlush(eq);

        User organizer = getOrCreateUser("USER_ORG_FLOW", "Organizer Flow", "flow.org@example.com");
        User attendee = getOrCreateUser("USER_ATT_FLOW", "Attendee Flow", "flow.att@example.com");

        LocalDateTime start = LocalDateTime.now().plusDays(2).withHour(10).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusHours(2);

        com.ttcs.meetingmanagement.dto.CreateMeetingRequest request = new com.ttcs.meetingmanagement.dto.CreateMeetingRequest();
        request.setOrganizerId(organizer.getUserId());
        request.setRoomId(roomId);
        request.setTitle("Cuộc họp tổng kết quý");
        request.setDescription("Bàn về kế hoạch quý tới");
        request.setStartTime(start);
        request.setEndTime(end);
        request.setParticipants(List.of(attendee.getUserId(), "new_guest@example.com"));
        request.setEquipmentIds(List.of(eq.getEquipmentId()));

        // Gọi service tạo lịch họp
        com.ttcs.meetingmanagement.dto.MeetingResponse response = meetingService.createMeeting(request);

        assertNotNull(response.getMeetingId());
        assertEquals("SCHEDULED", response.getStatus());

        // 1. Kiểm tra phòng đã được cập nhật thành OCCUPIED trong database
        Room updatedRoom = roomRepository.findById(roomId).orElseThrow();
        assertEquals("OCCUPIED", updatedRoom.getStatus());

        // 2. Kiểm tra thiết bị đã được cập nhật thành IN_USE trong database
        Equipment updatedEq = equipmentRepository.findById(eq.getEquipmentId()).orElseThrow();
        assertEquals("IN_USE", updatedEq.getStatus());

        // 3. Kiểm tra lưu đúng người tham dự vào bảng meeting_participant
        List<MeetingParticipant> participants = participantRepository.findByMeetingId(response.getMeetingId());
        assertTrue(participants.size() >= 3); // host + attendee + new_guest
        assertTrue(participants.stream().anyMatch(p -> p.getUserId().equals(organizer.getUserId()) && "ACCEPTED".equals(p.getStatus())));
        assertTrue(participants.stream().anyMatch(p -> p.getUserId().equals(attendee.getUserId()) && "INVITED".equals(p.getStatus())));

        // 4. Kiểm tra lưu đúng thiết bị vào bảng meeting_equipment
        List<com.ttcs.meetingmanagement.model.MeetingEquipment> meetingEqs = meetingEquipmentRepository.findById_MeetingId(response.getMeetingId());
        assertEquals(1, meetingEqs.size());
        assertEquals(eq.getEquipmentId(), meetingEqs.get(0).getEquipmentId());
        assertEquals(1, meetingEqs.get(0).getQuantity());
    }

    @Test
    @Transactional
    void testPermissionCheckOnlyAdminAndProjectManagerCanManageMeetings() {
        // 1. Tạo user với Role thường (EMPLOYEE / USER)
        Role userRole = roleRepository.findById("ROLE_EMPLOYEE").orElseGet(() ->
                roleRepository.saveAndFlush(new Role("ROLE_EMPLOYEE", "EMPLOYEE")));
        User normalUser = getOrCreateUserWithRole("USER_NORMAL", "Normal Employee", "emp@example.com", userRole);

        // 2. Tạo user với Role PM
        Role pmRole = roleRepository.findById("ROLE_PM").orElseGet(() ->
                roleRepository.saveAndFlush(new Role("ROLE_PM", "PROJECT_MANAGER")));
        User pmUser = getOrCreateUserWithRole("USER_PM", "Project Manager", "pm@example.com", pmRole);

        String roomId = "ROOM_PERM_TEST";
        getOrCreateRoom(roomId, "Phòng Họp Permission", 10);

        LocalDateTime start = LocalDateTime.now().plusDays(3).withHour(14).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusHours(1);

        com.ttcs.meetingmanagement.dto.CreateMeetingRequest request = new com.ttcs.meetingmanagement.dto.CreateMeetingRequest();
        request.setOrganizerId(normalUser.getUserId());
        request.setRoomId(roomId);
        request.setTitle("Cuộc họp của nhân viên thường");
        request.setStartTime(start);
        request.setEndTime(end);

        // A. Thử tạo cuộc họp bằng Normal User -> Bị chặn 403 FORBIDDEN
        com.ttcs.meetingmanagement.exception.MeetingException forbiddenError = assertThrows(
                com.ttcs.meetingmanagement.exception.MeetingException.class,
                () -> meetingService.createMeeting(request)
        );
        assertEquals(HttpStatus.FORBIDDEN, forbiddenError.getStatus());

        // B. Tạo cuộc họp bằng PM User -> Thành công!
        request.setOrganizerId(pmUser.getUserId());
        request.setTitle("Cuộc họp của PM");
        com.ttcs.meetingmanagement.dto.MeetingResponse pmResponse = meetingService.createMeeting(request);
        assertNotNull(pmResponse.getMeetingId());

        // C. Thử sửa cuộc họp bằng Normal User -> Bị chặn 403 FORBIDDEN
        MeetingController.MeetingRequest updateReq = new MeetingController.MeetingRequest(
                "Cuộc họp cập nhật", "Mô tả", start, end, pmUser.getUserId(), List.of(), "NONE", 0, roomId, List.of()
        );
        ResponseStatusException updateForbidden = assertThrows(
                ResponseStatusException.class,
                () -> meetingController.update(pmResponse.getMeetingId(), updateReq, normalUser.getUserId(), null)
        );
        assertEquals(HttpStatus.FORBIDDEN, updateForbidden.getStatusCode());

        // D. Sửa cuộc họp bằng PM User -> Thành công!
        Meeting updatedMeeting = meetingController.update(pmResponse.getMeetingId(), updateReq, pmUser.getUserId(), null);
        assertEquals("Cuộc họp cập nhật", updatedMeeting.getTitle());

        // E. Thử hủy cuộc họp bằng Normal User -> Bị chặn 403 FORBIDDEN
        ResponseStatusException cancelForbidden = assertThrows(
                ResponseStatusException.class,
                () -> meetingController.cancel(pmResponse.getMeetingId(), normalUser.getUserId(), null)
        );
        assertEquals(HttpStatus.FORBIDDEN, cancelForbidden.getStatusCode());

        // F. Hủy cuộc họp bằng PM User -> Thành công!
        meetingController.cancel(pmResponse.getMeetingId(), pmUser.getUserId(), null);
        Meeting cancelledMeeting = meetingRepository.findById(pmResponse.getMeetingId()).orElseThrow();
        assertEquals("CANCELLED", cancelledMeeting.getStatus());
    }

    @Test
    @Transactional
    void testCreateMeeting_WithRoomNameP103_ResolvesToRoomIdAndSavesSuccessfully() {
        // Setup room ROOM003 - P.103
        Room room = getOrCreateRoom("ROOM003", "P.103", 20);
        User adminUser = getOrCreateUser("USER001", "Admin User", "admin@company.com");

        LocalDateTime start = LocalDateTime.now().plusDays(2).withHour(10).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusHours(1);

        // 1. Nhập P103 (không có dấu chấm) - kiểm tra lỗi ban đầu của người dùng
        MeetingController.MeetingRequest requestP103 = new MeetingController.MeetingRequest(
                "Họp Dự Án P103",
                "Mô tả họp P103",
                start,
                end,
                adminUser.getUserId(),
                List.of("member1@company.com"),
                "NONE",
                0,
                "P103", // Gửi P103
                List.of()
        );

        Meeting created1 = meetingController.create(requestP103, adminUser.getUserId(), null);
        assertNotNull(created1);
        assertEquals("ROOM003", created1.getRoomId(), "Cuộc họp phải được lưu với đúng roomId ROOM003 trong DB");

        Room updatedRoom = roomRepository.findById("ROOM003").orElseThrow();
        assertEquals("OCCUPIED", updatedRoom.getStatus(), "Trạng thái phòng phải chuyển thành OCCUPIED khi đặt thành công");

        // 2. Nhập P.103 (tên phòng đầy đủ)
        LocalDateTime start2 = start.plusDays(1);
        LocalDateTime end2 = start2.plusHours(1);
        MeetingController.MeetingRequest requestPDot103 = new MeetingController.MeetingRequest(
                "Họp Dự Án P.103",
                "Mô tả họp P.103",
                start2,
                end2,
                adminUser.getUserId(),
                List.of("member1@company.com"),
                "NONE",
                0,
                "P.103", // Gửi P.103
                List.of()
        );
        Meeting created2 = meetingController.create(requestPDot103, adminUser.getUserId(), null);
        assertNotNull(created2);
        assertEquals("ROOM003", created2.getRoomId(), "Cuộc họp gửi P.103 phải lưu đúng roomId ROOM003");

        // 3. Nhập đúng roomId ROOM003
        LocalDateTime start3 = start.plusDays(2);
        LocalDateTime end3 = start3.plusHours(1);
        MeetingController.MeetingRequest requestRoomId = new MeetingController.MeetingRequest(
                "Họp Dự Án ROOM003",
                "Mô tả họp ROOM003",
                start3,
                end3,
                adminUser.getUserId(),
                List.of("member1@company.com"),
                "NONE",
                0,
                "ROOM003", // Gửi ROOM003
                List.of()
        );
        Meeting created3 = meetingController.create(requestRoomId, adminUser.getUserId(), null);
        assertNotNull(created3);
        assertEquals("ROOM003", created3.getRoomId(), "Cuộc họp gửi ROOM003 phải lưu đúng roomId ROOM003");

        // 4. Nhập phòng không tồn tại (P999) -> báo 404 NOT_FOUND
        LocalDateTime start4 = start.plusDays(3);
        LocalDateTime end4 = start4.plusHours(1);
        MeetingController.MeetingRequest requestInvalid = new MeetingController.MeetingRequest(
                "Họp Phòng Không Tồn Tại",
                "Mô tả",
                start4,
                end4,
                adminUser.getUserId(),
                List.of("member1@company.com"),
                "NONE",
                0,
                "P999",
                List.of()
        );
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                meetingController.create(requestInvalid, adminUser.getUserId(), null));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Phòng họp không tồn tại: P999"));
    }

    private User getOrCreateUser(String userId, String name, String email) {
        Role adminRole = roleRepository.findById("ROLE_ADMIN").orElseGet(() ->
                roleRepository.saveAndFlush(new Role("ROLE_ADMIN", "ADMIN")));
        return getOrCreateUserWithRole(userId, name, email, adminRole);
    }

    private User getOrCreateUserWithRole(String userId, String name, String email, Role role) {
        return userRepository.findById(userId).orElseGet(() -> {
            User user = new User();
            user.setUserId(userId);
            user.setFullName(name);
            user.setEmail(email);
            user.setStatus("ACTIVE");
            user.setRole(role);
            return userRepository.saveAndFlush(user);
        });
    }

    private Room getOrCreateRoom(String roomId, String name, int capacity) {
        return roomRepository.findById(roomId).orElseGet(() -> {
            Room room = new Room();
            room.setRoomId(roomId);
            room.setRoomName(name);
            room.setCapacity(capacity);
            room.setStatus("AVAILABLE");
            return roomRepository.saveAndFlush(room);
        });
    }

    private Equipment saveEquipment(String room, String name, int quantity) {
        Equipment equipment = new Equipment();
        equipment.setEquipmentId("EQ-" + java.util.UUID.randomUUID());
        equipment.setRoomId(room);
        equipment.setEquipmentName(name);
        equipment.setType("Test");
        equipment.setTotalQuantity(quantity);
        equipment.setStatus("AVAILABLE");
        return equipmentRepository.saveAndFlush(equipment);
    }

    private void saveReservation(
            Equipment equipment,
            String room,
            int quantity,
            OffsetDateTime start,
            OffsetDateTime end,
            EquipmentBookingStatus status
    ) {
        EquipmentBooking reservation = new EquipmentBooking();
        reservation.setEquipment(equipment);
        reservation.setRoom(room);
        reservation.setQuantity(quantity);
        reservation.setStartTime(start);
        reservation.setEndTime(end);
        reservation.setStatus(status);
        bookingRepository.save(reservation);
    }
}
