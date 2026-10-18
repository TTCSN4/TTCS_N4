package com.ttcs.meetingmanagement;

import com.ttcs.meetingmanagement.controller.MeetingController;
import com.ttcs.meetingmanagement.dto.CreateMeetingRequest;
import com.ttcs.meetingmanagement.dto.MeetingResponse;
import com.ttcs.meetingmanagement.equipment.Equipment;
import com.ttcs.meetingmanagement.equipment.EquipmentController;
import com.ttcs.meetingmanagement.equipment.EquipmentRepository;
import com.ttcs.meetingmanagement.exception.MeetingException;
import com.ttcs.meetingmanagement.model.EquipmentAvailabilityStatus;
import com.ttcs.meetingmanagement.model.EquipmentBooking;
import com.ttcs.meetingmanagement.model.EquipmentBookingStatus;
import com.ttcs.meetingmanagement.model.Meeting;
import com.ttcs.meetingmanagement.model.MeetingEquipment;
import com.ttcs.meetingmanagement.model.MeetingParticipant;
import com.ttcs.meetingmanagement.model.Role;
import com.ttcs.meetingmanagement.model.User;
import com.ttcs.meetingmanagement.repository.EquipmentBookingRepository;
import com.ttcs.meetingmanagement.repository.MeetingEquipmentRepository;
import com.ttcs.meetingmanagement.repository.MeetingParticipantRepository;
import com.ttcs.meetingmanagement.repository.MeetingRepository;
import com.ttcs.meetingmanagement.repository.RoleRepository;
import com.ttcs.meetingmanagement.repository.UserRepository;
import com.ttcs.meetingmanagement.room.Room;
import com.ttcs.meetingmanagement.room.RoomRepository;
import com.ttcs.meetingmanagement.service.MeetingService;

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
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:meeting_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.mail.host=localhost",
        "spring.mail.port=1025"
})
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
    private MeetingService meetingService;

    @Autowired
    private MeetingEquipmentRepository meetingEquipmentRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private MeetingController meetingController;

    @PersistenceContext
    private EntityManager entityManager;

    // ==========================================
    // DEVELOP TEST 1: KIEM TRA LUU VA DOC CUOC HOP THEO ERD
    // ==========================================

    @Test
    @Transactional
    void meetingQueriesWorkWithStringIdAndLocalDateTime() {
        User organizer = getOrCreateUser("USER_ORG_TEST", "Organizer Test", "org@example.com");
        User attendee = getOrCreateUser("USER_ATT_TEST", "Attendee Test", "attendee@example.com");

        LocalDateTime start = LocalDateTime.of(2030, 7, 10, 9, 0);

        Meeting meeting = new Meeting();
        meeting.setMeetingId(UUID.randomUUID().toString());
        meeting.setTitle("Meeting test");
        meeting.setDescription("Test meeting repository");
        meeting.setStartTime(start);
        meeting.setEndTime(start.plusHours(1));
        meeting.setOrganizerId(organizer.getUserId());
        meeting.setStatus("SCHEDULED");
        meeting.setIsRecurring(false);
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

        assertEquals(saved.getMeetingId(), loaded.getMeetingId());
        assertEquals("Meeting test", loaded.getTitle());
        assertEquals(start, loaded.getStartTime());
        assertEquals(start.plusHours(1), loaded.getEndTime());
        assertEquals("SCHEDULED", loaded.getStatus());

        List<MeetingParticipant> participants = participantRepository.findByMeetingId(loaded.getMeetingId());
        assertEquals(1, participants.size());
        assertEquals(attendee.getUserId(), participants.get(0).getUserId());
    }

    // ==========================================
    // DEVELOP TEST 2: KIEM TRA TRANG THAI THIET BI
    // ==========================================

    @Test
    @Transactional
    void statusAtTimeReportsAvailableBookedAndMaintenanceQuantities() {
        String room = "P-status-query";
        getOrCreateRoom(room, "Phòng Test Status Query", 20);

        Equipment equipment = saveEquipment(room, "Test projector", 3);

        OffsetDateTime start = OffsetDateTime.parse("2030-05-10T09:00:00+07:00");
        saveReservation(equipment, room, 1, start, start.plusHours(1), EquipmentBookingStatus.BOOKED);
        saveReservation(equipment, room, 1, start, start.plusHours(1), EquipmentBookingStatus.MAINTENANCE);

        EquipmentController.EquipmentStatusResponse during = equipmentController
                .status(room, start.plusMinutes(30))
                .get(0);

        assertEquals(3, during.totalQuantity());
        assertEquals(1, during.bookedQuantity());
        assertEquals(1, during.maintenanceQuantity());
        assertEquals(1, during.availableQuantity());
        assertEquals(
                List.of(
                        EquipmentAvailabilityStatus.AVAILABLE,
                        EquipmentBookingStatus.BOOKED.name().equals("BOOKED") ? EquipmentAvailabilityStatus.BOOKED : EquipmentAvailabilityStatus.AVAILABLE,
                        EquipmentAvailabilityStatus.MAINTENANCE
                ),
                during.statuses()
        );

        EquipmentController.EquipmentStatusResponse after = equipmentController
                .status(room, start.plusHours(1))
                .get(0);

        assertEquals(3, after.totalQuantity());
        assertEquals(3, after.availableQuantity());
        assertTrue(after.statuses().contains(EquipmentAvailabilityStatus.AVAILABLE));
        assertEquals(1, after.statuses().size());
    }

    // ==========================================
    // DEVELOP TEST 3: KIEM TRA DAT THIET BI TRUNG LICH
    // ==========================================

    @Test
    @Transactional
    void bookingRejectsOverlappingRequestsBeyondInventory() {
        String room = "P-booking-conflict";
        getOrCreateRoom(room, "Phòng Test Conflict", 15);

        Equipment equipment = saveEquipment(room, "One projector", 1);
        OffsetDateTime start = OffsetDateTime.parse("2030-06-10T09:00:00+07:00");
        saveReservation(equipment, room, 1, start, start.plusHours(1), EquipmentBookingStatus.BOOKED);

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> equipmentController.book(
                        new EquipmentController.EquipmentBookingRequest(
                                equipment.getEquipmentId(),
                                null,
                                room,
                                1,
                                start.plusMinutes(15),
                                start.plusMinutes(45)
                        )
                )
        );

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
    }

    // ==========================================
    // US01 TEST SCENARIOS (1 - 15)
    // ==========================================

    // Scenario 1 & 10: ADMIN tạo cuộc họp hợp lệ – thành công và lưu đầy đủ 4 bảng
    @Test
    @Transactional
    void test01_AdminCreateMeeting_SuccessAndFullPersistence() {
        String roomId = "ROOM_SCENARIO_01";
        Room room = getOrCreateRoom(roomId, "Phòng Họp Ban Giám Đốc", 10);
        Equipment eq = saveEquipment(roomId, "Micro Không Dây", 4);
        User admin = getOrCreateUser("USER_ADMIN_01", "Admin Chính", "admin01@company.com");
        User attendee = getOrCreateUser("USER_ATT_01", "Khách Mời 1", "att01@company.com");

        LocalDateTime start = LocalDateTime.now().plusDays(2).withHour(9).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusHours(2);

        CreateMeetingRequest req = new CreateMeetingRequest();
        req.setOrganizerId(admin.getUserId());
        req.setRoomId(roomId);
        req.setTitle("Họp Chiến Lược Quý 1");
        req.setDescription("Bàn chiến lược");
        req.setStartTime(start);
        req.setEndTime(end);
        req.setParticipants(List.of(attendee.getUserId()));
        req.setEquipmentIds(List.of(eq.getEquipmentId(), eq.getEquipmentId()));

        MeetingResponse resp = meetingService.createMeeting(req);
        assertNotNull(resp);
        assertNotNull(resp.getMeetingId());
        assertEquals("SCHEDULED", resp.getStatus());

        // Kiểm tra Meeting được lưu vào DB
        Meeting savedMeeting = meetingRepository.findById(resp.getMeetingId()).orElseThrow();
        assertEquals("Họp Chiến Lược Quý 1", savedMeeting.getTitle());
        assertEquals(roomId, savedMeeting.getRoomId());

        // Kiểm tra trạng thái phòng được cập nhật thành OCCUPIED
        Room updatedRoom = roomRepository.findById(roomId).orElseThrow();
        assertEquals("OCCUPIED", updatedRoom.getStatus());

        // Kiểm tra người tham dự được lưu vào bảng meeting_participant
        List<MeetingParticipant> participants = participantRepository.findByMeetingId(resp.getMeetingId());
        assertTrue(participants.stream().anyMatch(p -> p.getUserId().equals(admin.getUserId())));
        assertTrue(participants.stream().anyMatch(p -> p.getUserId().equals(attendee.getUserId())));

        // Kiểm tra thiết bị được lưu vào bảng meeting_equipment
        List<MeetingEquipment> meetingEqs = meetingEquipmentRepository.findById_MeetingId(resp.getMeetingId());
        assertEquals(1, meetingEqs.size());
        assertEquals(eq.getEquipmentId(), meetingEqs.get(0).getEquipmentId());
        assertEquals(2, meetingEqs.get(0).getQuantity());

        // Kiểm tra thiết bị được lưu vào bảng equipment_bookings
        List<EquipmentBooking> eqBookings = bookingRepository.findAll().stream()
                .filter(b -> b.getMeetingId() != null && b.getMeetingId().equals(resp.getMeetingId()))
                .toList();
        assertEquals(1, eqBookings.size());
        assertEquals(2, eqBookings.get(0).getQuantity());
    }

    // Scenario 2: PM tạo cuộc họp hợp lệ – thành công
    @Test
    @Transactional
    void test02_ProjectManagerCreateMeeting_Success() {
        Role pmRole = roleRepository.findById("ROLE_PM").orElseGet(() ->
                roleRepository.saveAndFlush(new Role("ROLE_PM", "PROJECT_MANAGER")));
        User pm = getOrCreateUserWithRole("USER_PM_02", "Trưởng Dự Án A", "pm02@company.com", pmRole);
        String roomId = "ROOM_SCENARIO_02";
        getOrCreateRoom(roomId, "Phòng Họp Dự Án", 10);

        LocalDateTime start = LocalDateTime.now().plusDays(2).withHour(14).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusHours(1);

        CreateMeetingRequest req = new CreateMeetingRequest();
        req.setOrganizerId(pm.getUserId());
        req.setRoomId(roomId);
        req.setTitle("Họp Sprint Review");
        req.setStartTime(start);
        req.setEndTime(end);

        MeetingResponse resp = meetingService.createMeeting(req);
        assertNotNull(resp);
        assertNotNull(resp.getMeetingId());
        assertEquals("SCHEDULED", resp.getStatus());
    }

    // Scenario 3: MEMBER tạo cuộc họp – HTTP 403 FORBIDDEN
    @Test
    @Transactional
    void test03_MemberCreateMeeting_Forbidden403() {
        Role memberRole = roleRepository.findById("ROLE_EMPLOYEE").orElseGet(() ->
                roleRepository.saveAndFlush(new Role("ROLE_EMPLOYEE", "EMPLOYEE")));
        User member = getOrCreateUserWithRole("USER_MEMBER_03", "Thành Viên", "member03@company.com", memberRole);
        String roomId = "ROOM_SCENARIO_03";
        getOrCreateRoom(roomId, "Phòng Thường", 10);

        LocalDateTime start = LocalDateTime.now().plusDays(2).withHour(10).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusHours(1);

        CreateMeetingRequest req = new CreateMeetingRequest();
        req.setOrganizerId(member.getUserId());
        req.setRoomId(roomId);
        req.setTitle("Thử Tạo Cuộc Họp");
        req.setStartTime(start);
        req.setEndTime(end);

        MeetingException ex = assertThrows(MeetingException.class, () -> meetingService.createMeeting(req));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    // Scenario 4: Người chưa đăng nhập – HTTP 401 UNAUTHORIZED
    @Test
    @Transactional
    void test04_UnauthenticatedUser_Unauthorized401() {
        String roomId = "ROOM_SCENARIO_04";
        getOrCreateRoom(roomId, "Phòng 404", 10);

        LocalDateTime start = LocalDateTime.now().plusDays(2).withHour(10).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusHours(1);

        CreateMeetingRequest req = new CreateMeetingRequest();
        req.setOrganizerId(null);
        req.setRoomId(roomId);
        req.setTitle("Cuộc Họp Vô Danh");
        req.setStartTime(start);
        req.setEndTime(end);

        MeetingException ex = assertThrows(MeetingException.class, () -> meetingService.createMeeting(req));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
    }

    // Scenario 5: Phòng trùng lịch – từ chối 409 CONFLICT
    @Test
    @Transactional
    void test05_RoomOverlapConflict_RejectsWith409() {
        User admin = getOrCreateUser("USER_ADMIN_05", "Admin 05", "admin05@company.com");
        String roomId = "ROOM_SCENARIO_05";
        getOrCreateRoom(roomId, "Phòng Trùng Lịch", 10);

        LocalDateTime start = LocalDateTime.now().plusDays(3).withHour(9).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusHours(2);

        CreateMeetingRequest req1 = new CreateMeetingRequest();
        req1.setOrganizerId(admin.getUserId());
        req1.setRoomId(roomId);
        req1.setTitle("Họp Lần 1");
        req1.setStartTime(start);
        req1.setEndTime(end);
        meetingService.createMeeting(req1);

        // Họp lần 2 trùng khung giờ (9h30 - 10h30)
        CreateMeetingRequest req2 = new CreateMeetingRequest();
        req2.setOrganizerId(admin.getUserId());
        req2.setRoomId(roomId);
        req2.setTitle("Họp Lần 2 Trùng Lịch");
        req2.setStartTime(start.plusMinutes(30));
        req2.setEndTime(start.plusMinutes(90));

        MeetingException ex = assertThrows(MeetingException.class, () -> meetingService.createMeeting(req2));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertTrue(ex.getMessage().contains("đã được đặt") || ex.getMessage().contains("khoảng thời gian"));
    }

    // Scenario 6: Phòng bảo trì – từ chối 400 BAD_REQUEST
    @Test
    @Transactional
    void test06_RoomUnderMaintenance_RejectsWith400() {
        User admin = getOrCreateUser("USER_ADMIN_06", "Admin 06", "admin06@company.com");
        String roomId = "ROOM_MAINT_06";
        Room room = getOrCreateRoom(roomId, "Phòng Đang Sơn Sửa", 10);
        room.setStatus("MAINTENANCE");
        roomRepository.saveAndFlush(room);

        LocalDateTime start = LocalDateTime.now().plusDays(2).withHour(9).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusHours(1);

        CreateMeetingRequest req = new CreateMeetingRequest();
        req.setOrganizerId(admin.getUserId());
        req.setRoomId(roomId);
        req.setTitle("Họp Phòng Bảo Trì");
        req.setStartTime(start);
        req.setEndTime(end);

        MeetingException ex = assertThrows(MeetingException.class, () -> meetingService.createMeeting(req));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("bảo trì"));
    }

    // Scenario 7: Vượt sức chứa phòng – từ chối 400 BAD_REQUEST
    @Test
    @Transactional
    void test07_ExceedRoomCapacity_RejectsWith400() {
        User admin = getOrCreateUser("USER_ADMIN_07", "Admin 07", "admin07@company.com");
        User user1 = getOrCreateUser("USER_U1_07", "User 1", "u1_07@company.com");
        User user2 = getOrCreateUser("USER_U2_07", "User 2", "u2_07@company.com");
        User user3 = getOrCreateUser("USER_U3_07", "User 3", "u3_07@company.com");
        String roomId = "ROOM_CAP_07";
        Room room = getOrCreateRoom(roomId, "Phòng Nhỏ 2 Người", 2);
        room.setCapacity(2);
        roomRepository.saveAndFlush(room);

        LocalDateTime start = LocalDateTime.now().plusDays(2).withHour(9).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusHours(1);

        CreateMeetingRequest req = new CreateMeetingRequest();
        req.setOrganizerId(admin.getUserId());
        req.setRoomId(roomId);
        req.setTitle("Họp Quá Tải");
        req.setStartTime(start);
        req.setEndTime(end);
        // Admin (1) + User1 (1) + User2 (1) + User3 (1) = 4 người > sức chứa 2
        req.setParticipants(List.of(user1.getUserId(), user2.getUserId(), user3.getUserId()));

        MeetingException ex = assertThrows(MeetingException.class, () -> meetingService.createMeeting(req));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("vượt quá sức chứa"));
    }

    // Scenario 8: Thiết bị không đủ số lượng – từ chối 409 CONFLICT
    @Test
    @Transactional
    void test08_InsufficientEquipment_RejectsWith409() {
        User admin = getOrCreateUser("USER_ADMIN_08", "Admin 08", "admin08@company.com");
        String roomId = "ROOM_EQ_08";
        getOrCreateRoom(roomId, "Phòng Họp 08", 10);
        Equipment eq = saveEquipment(roomId, "Bảng Điện Tử", 2);

        LocalDateTime start = LocalDateTime.now().plusDays(2).withHour(9).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusHours(1);

        CreateMeetingRequest req = new CreateMeetingRequest();
        req.setOrganizerId(admin.getUserId());
        req.setRoomId(roomId);
        req.setTitle("Họp Thiếu Thiết Bị");
        req.setStartTime(start);
        req.setEndTime(end);
        // Yêu cầu 3 thiết bị trong khi tổng số lượng chỉ có 2
        req.setEquipmentIds(List.of(eq.getEquipmentId(), eq.getEquipmentId(), eq.getEquipmentId()));

        MeetingException ex = assertThrows(MeetingException.class, () -> meetingService.createMeeting(req));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertTrue(ex.getMessage().toLowerCase().contains("không đủ"));
    }

    // Scenario 9: Người tham dự không tồn tại – từ chối 404 NOT_FOUND
    @Test
    @Transactional
    void test09_NonExistentParticipant_RejectsWith404() {
        User admin = getOrCreateUser("USER_ADMIN_09", "Admin 09", "admin09@company.com");
        String roomId = "ROOM_ATT_09";
        getOrCreateRoom(roomId, "Phòng Họp 09", 10);

        LocalDateTime start = LocalDateTime.now().plusDays(2).withHour(9).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusHours(1);

        CreateMeetingRequest req = new CreateMeetingRequest();
        req.setOrganizerId(admin.getUserId());
        req.setRoomId(roomId);
        req.setTitle("Họp Với Người Lạ");
        req.setStartTime(start);
        req.setEndTime(end);
        req.setParticipants(List.of("ghost_user_not_found@company.com"));

        MeetingException ex = assertThrows(MeetingException.class, () -> meetingService.createMeeting(req));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertTrue(ex.getMessage().contains("không tồn tại"));
    }

    // Scenario 11: Lưu một thành phần thất bại – rollback toàn bộ
    @Test
    @Transactional
    void test11_ComponentFailure_RollbackEntireTransaction() {
        User admin = getOrCreateUser("USER_ADMIN_11", "Admin 11", "admin11@company.com");
        String roomId = "ROOM_RB_11";
        Room r = getOrCreateRoom(roomId, "Phòng Họp Rollback", 10);
        r.setStatus("AVAILABLE");
        roomRepository.saveAndFlush(r);

        Equipment eq = saveEquipment(roomId, "Tivi 85 Inch", 1);

        LocalDateTime start = LocalDateTime.now().plusDays(4).withHour(9).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusHours(1);

        CreateMeetingRequest req = new CreateMeetingRequest();
        req.setOrganizerId(admin.getUserId());
        req.setRoomId(roomId);
        req.setTitle("Họp Sẽ Bị Rollback");
        req.setStartTime(start);
        req.setEndTime(end);
        // Đặt thiết bị vượt quá số lượng để trigger failure ở bước kiểm tra/đặt thiết bị
        req.setEquipmentIds(List.of(eq.getEquipmentId(), eq.getEquipmentId(), eq.getEquipmentId(), eq.getEquipmentId(), eq.getEquipmentId()));

        assertThrows(MeetingException.class, () -> meetingService.createMeeting(req));

        // Kiểm tra không có meeting nào được tạo với title này
        List<Meeting> meetings = meetingRepository.findAll().stream()
                .filter(m -> "Họp Sẽ Bị Rollback".equals(m.getTitle()))
                .toList();
        assertTrue(meetings.isEmpty(), "Meeting không được tồn tại trong DB khi gặp lỗi rollback");

        // Kiểm tra trạng thái phòng vẫn là AVAILABLE
        Room room = roomRepository.findById(roomId).orElseThrow();
        assertEquals("AVAILABLE", room.getStatus(), "Trạng thái phòng không được chuyển sang OCCUPIED khi rollback");
    }

    // Scenario 12: Hai yêu cầu đồng thời tạo cuộc họp cùng phòng/khung giờ – không được đặt trùng
    @Test
    void test12_ConcurrentRoomBooking_NoDoubleBooking() throws InterruptedException, ExecutionException {
        User admin = getOrCreateUser("USER_ADMIN_12", "Admin 12", "admin12@company.com");
        String roomId = "ROOM_CONCURRENT_12";
        getOrCreateRoom(roomId, "Phòng Cạnh Tranh", 10);

        LocalDateTime start = LocalDateTime.now().plusDays(5).withHour(10).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusHours(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        Callable<Boolean> task = () -> {
            CreateMeetingRequest req = new CreateMeetingRequest();
            req.setOrganizerId(admin.getUserId());
            req.setRoomId(roomId);
            req.setTitle("Cuộc họp đồng thời " + UUID.randomUUID());
            req.setStartTime(start);
            req.setEndTime(end);
            try {
                MeetingResponse resp = meetingService.createMeeting(req);
                if (resp != null && resp.getMeetingId() != null) {
                    successCount.incrementAndGet();
                    return true;
                }
            } catch (MeetingException e) {
                if (e.getStatus() == HttpStatus.CONFLICT) {
                    conflictCount.incrementAndGet();
                }
            }
            return false;
        };

        Future<Boolean> f1 = executor.submit(task);
        Future<Boolean> f2 = executor.submit(task);
        f1.get();
        f2.get();
        executor.shutdown();

        assertEquals(1, successCount.get(), "Chỉ đúng 1 yêu cầu được phép đặt phòng thành công");
        assertEquals(1, conflictCount.get(), "Yêu cầu thứ hai phải bị từ chối 409 CONFLICT");
    }

    // Scenario 13: Hai yêu cầu đồng thời cùng đặt thiết bị – không vượt số lượng
    @Test
    void test13_ConcurrentEquipmentBooking_InventoryNotExceeded() throws InterruptedException, ExecutionException {
        User admin = getOrCreateUser("USER_ADMIN_13", "Admin 13", "admin13@company.com");
        String room1 = "ROOM_CONC_EQ_1";
        String room2 = "ROOM_CONC_EQ_2";
        getOrCreateRoom(room1, "Phòng A", 10);
        getOrCreateRoom(room2, "Phòng B", 10);

        // Chỉ có 1 máy chiếu duy nhất
        Equipment eq = saveEquipment(room1, "Máy Chiếu Độc Bản", 1);

        LocalDateTime start = LocalDateTime.now().plusDays(6).withHour(10).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusHours(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        Callable<Boolean> task1 = () -> {
            CreateMeetingRequest req = new CreateMeetingRequest();
            req.setOrganizerId(admin.getUserId());
            req.setRoomId(room1);
            req.setTitle("Đặt Thiết Bị 1");
            req.setStartTime(start);
            req.setEndTime(end);
            req.setEquipmentIds(List.of(eq.getEquipmentId()));
            try {
                meetingService.createMeeting(req);
                successCount.incrementAndGet();
                return true;
            } catch (MeetingException e) {
                if (e.getStatus() == HttpStatus.CONFLICT) {
                    conflictCount.incrementAndGet();
                }
            }
            return false;
        };

        Callable<Boolean> task2 = () -> {
            CreateMeetingRequest req = new CreateMeetingRequest();
            req.setOrganizerId(admin.getUserId());
            req.setRoomId(room2);
            req.setTitle("Đặt Thiết Bị 2");
            req.setStartTime(start);
            req.setEndTime(end);
            req.setEquipmentIds(List.of(eq.getEquipmentId()));
            try {
                meetingService.createMeeting(req);
                successCount.incrementAndGet();
                return true;
            } catch (MeetingException e) {
                if (e.getStatus() == HttpStatus.CONFLICT) {
                    conflictCount.incrementAndGet();
                }
            }
            return false;
        };

        Future<Boolean> f1 = executor.submit(task1);
        Future<Boolean> f2 = executor.submit(task2);
        f1.get();
        f2.get();
        executor.shutdown();

        assertEquals(1, successCount.get(), "Chỉ đúng 1 yêu cầu được mượn thiết bị thành công");
        assertEquals(1, conflictCount.get(), "Yêu cầu thứ hai phải bị từ chối 409 CONFLICT");
    }

    // Scenario 14: Sau khi tạo cuộc họp, API phòng trống và thiết bị khả dụng phản ánh đúng dữ liệu
    @Test
    @Transactional
    void test14_AvailabilitySynchronization_ReflectsBookedData() {
        User admin = getOrCreateUser("USER_ADMIN_14", "Admin 14", "admin14@company.com");
        String roomId = "ROOM_SYNC_14";
        getOrCreateRoom(roomId, "Phòng Kiểm Tra Đồng Bộ", 10);
        Equipment eq = saveEquipment(roomId, "Loa Bluetooth", 3);

        LocalDateTime start = LocalDateTime.now().plusDays(7).withHour(10).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusHours(2);

        CreateMeetingRequest req = new CreateMeetingRequest();
        req.setOrganizerId(admin.getUserId());
        req.setRoomId(roomId);
        req.setTitle("Họp Kiểm Tra Đồng Bộ");
        req.setStartTime(start);
        req.setEndTime(end);
        req.setEquipmentIds(List.of(eq.getEquipmentId(), eq.getEquipmentId()));

        MeetingResponse resp = meetingService.createMeeting(req);
        assertNotNull(resp);

        // 1. Kiểm tra trạng thái phòng đã chuyển thành OCCUPIED
        Room updatedRoom = roomRepository.findById(roomId).orElseThrow();
        assertEquals("OCCUPIED", updatedRoom.getStatus());

        // 2. Kiểm tra API thiết bị khả dụng phản ánh số lượng đã đặt
        OffsetDateTime offsetCheckTime = start.plusHours(1).atOffset(java.time.ZoneOffset.ofHours(7));
        List<EquipmentController.EquipmentStatusResponse> statuses = equipmentController.status(roomId, offsetCheckTime);
        assertFalse(statuses.isEmpty());
        EquipmentController.EquipmentStatusResponse statusResp = statuses.stream()
                .filter(s -> s.equipmentId().equals(eq.getEquipmentId()))
                .findFirst().orElseThrow();
        assertEquals(3, statusResp.totalQuantity());
        assertEquals(2, statusResp.bookedQuantity());
        assertEquals(1, statusResp.availableQuantity());
    }

    // US01 Flow & Permission Checks (HEAD tests)
    @Test
    @Transactional
    void testCreateMeetingFlowSavesRoomEquipmentParticipantsAndUpdatesStatuses() {
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

        CreateMeetingRequest request = new CreateMeetingRequest();
        request.setOrganizerId(organizer.getUserId());
        request.setRoomId(roomId);
        request.setTitle("Cuộc họp tổng kết quý");
        request.setDescription("Bàn về kế hoạch quý tới");
        request.setStartTime(start);
        request.setEndTime(end);
        request.setParticipants(List.of(attendee.getUserId()));
        request.setEquipmentIds(List.of(eq.getEquipmentId()));

        MeetingResponse response = meetingService.createMeeting(request);

        assertNotNull(response.getMeetingId());
        assertEquals("SCHEDULED", response.getStatus());

        Room updatedRoom = roomRepository.findById(roomId).orElseThrow();
        assertEquals("OCCUPIED", updatedRoom.getStatus());

        Equipment updatedEq = equipmentRepository.findById(eq.getEquipmentId()).orElseThrow();
        assertEquals("IN_USE", updatedEq.getStatus());

        List<MeetingParticipant> participants = participantRepository.findByMeetingId(response.getMeetingId());
        assertTrue(participants.size() >= 2);
        assertTrue(participants.stream().anyMatch(p -> p.getUserId().equals(organizer.getUserId())));
        assertTrue(participants.stream().anyMatch(p -> p.getUserId().equals(attendee.getUserId())));

        List<MeetingEquipment> meetingEqs = meetingEquipmentRepository.findById_MeetingId(response.getMeetingId());
        assertEquals(1, meetingEqs.size());
        assertEquals(eq.getEquipmentId(), meetingEqs.get(0).getEquipmentId());
        assertEquals(1, meetingEqs.get(0).getQuantity());
    }

    @Test
    @Transactional
    void testPermissionCheckOnlyAdminAndProjectManagerCanManageMeetings() {
        Role userRole = roleRepository.findById("ROLE_EMPLOYEE").orElseGet(() ->
                roleRepository.saveAndFlush(new Role("ROLE_EMPLOYEE", "EMPLOYEE")));
        User normalUser = getOrCreateUserWithRole("USER_NORMAL", "Normal Employee", "emp@example.com", userRole);

        Role pmRole = roleRepository.findById("ROLE_PM").orElseGet(() ->
                roleRepository.saveAndFlush(new Role("ROLE_PM", "PROJECT_MANAGER")));
        User pmUser = getOrCreateUserWithRole("USER_PM", "Project Manager", "pm@example.com", pmRole);

        String roomId = "ROOM_PERM_TEST";
        getOrCreateRoom(roomId, "Phòng Họp Permission", 10);

        LocalDateTime start = LocalDateTime.now().plusDays(3).withHour(14).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusHours(1);

        CreateMeetingRequest request = new CreateMeetingRequest();
        request.setOrganizerId(normalUser.getUserId());
        request.setRoomId(roomId);
        request.setTitle("Cuộc họp của nhân viên thường");
        request.setStartTime(start);
        request.setEndTime(end);

        // A. Thử tạo cuộc họp bằng Normal User -> Bị chặn 403 FORBIDDEN
        MeetingException forbiddenError = assertThrows(
                MeetingException.class,
                () -> meetingService.createMeeting(request)
        );
        assertEquals(HttpStatus.FORBIDDEN, forbiddenError.getStatus());

        // B. Tạo cuộc họp bằng PM User -> Thành công!
        request.setOrganizerId(pmUser.getUserId());
        request.setTitle("Cuộc họp của PM");
        MeetingResponse pmResponse = meetingService.createMeeting(request);
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
        Room room = getOrCreateRoom("ROOM003", "P.103", 20);
        User adminUser = getOrCreateUser("USER001", "Admin User", "admin@company.com");

        LocalDateTime start = LocalDateTime.now().plusDays(2).withHour(10).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusHours(1);

        MeetingController.MeetingRequest requestP103 = new MeetingController.MeetingRequest(
                "Họp Dự Án P103",
                "Mô tả họp P103",
                start,
                end,
                adminUser.getUserId(),
                List.of("member1@company.com"),
                "NONE",
                0,
                "P103",
                List.of()
        );

        Meeting created1 = meetingController.create(requestP103, adminUser.getUserId(), null);
        assertNotNull(created1);
        assertEquals("ROOM003", created1.getRoomId(), "Cuộc họp phải được lưu với đúng roomId ROOM003 trong DB");

        Room updatedRoom = roomRepository.findById("ROOM003").orElseThrow();
        assertEquals("OCCUPIED", updatedRoom.getStatus(), "Trạng thái phòng phải chuyển thành OCCUPIED khi đặt thành công");

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
                "P.103",
                List.of()
        );
        Meeting created2 = meetingController.create(requestPDot103, adminUser.getUserId(), null);
        assertNotNull(created2);
        assertEquals("ROOM003", created2.getRoomId(), "Cuộc họp gửi P.103 phải lưu đúng roomId ROOM003");

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
                "ROOM003",
                List.of()
        );
        Meeting created3 = meetingController.create(requestRoomId, adminUser.getUserId(), null);
        assertNotNull(created3);
        assertEquals("ROOM003", created3.getRoomId(), "Cuộc họp gửi ROOM003 phải lưu đúng roomId ROOM003");

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

    // ==========================================
    // HELPER METHODS
    // ==========================================

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
        equipment.setEquipmentId("EQ-" + UUID.randomUUID());
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
            EquipmentBookingStatus status) {
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
