package com.ttcs.meetingmanagement;

import com.ttcs.meetingmanagement.dto.AvailableRoomResponse;
import com.ttcs.meetingmanagement.dto.RoomAvailabilitySummaryResponse;
import com.ttcs.meetingmanagement.dto.RoomBookingRequest;
import com.ttcs.meetingmanagement.dto.RoomBookingResponse;
import com.ttcs.meetingmanagement.dto.RoomScheduleStatusResponse;
import com.ttcs.meetingmanagement.equipment.Equipment;
import com.ttcs.meetingmanagement.equipment.EquipmentController;
import com.ttcs.meetingmanagement.equipment.EquipmentRepository;
import com.ttcs.meetingmanagement.model.EquipmentAvailabilityStatus;
import com.ttcs.meetingmanagement.model.EquipmentBooking;
import com.ttcs.meetingmanagement.model.EquipmentBookingStatus;
import com.ttcs.meetingmanagement.model.Meeting;
import com.ttcs.meetingmanagement.model.MeetingStatus;
import com.ttcs.meetingmanagement.repository.EquipmentBookingRepository;
import com.ttcs.meetingmanagement.repository.MeetingRepository;
import com.ttcs.meetingmanagement.room.Room;
import com.ttcs.meetingmanagement.room.RoomAvailabilityService;
import com.ttcs.meetingmanagement.room.RoomRepository;
import com.ttcs.meetingmanagement.roombooking.RoomBookingService;

import com.ttcs.meetingmanagement.model.User;
import com.ttcs.meetingmanagement.model.Role;
import com.ttcs.meetingmanagement.repository.UserRepository;
import com.ttcs.meetingmanagement.repository.RoleRepository;
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

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:meeting_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
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
    private RoomRepository roomRepository;

    @Autowired
    private RoomAvailabilityService roomAvailabilityService;

    @Autowired
    private RoomBookingService roomBookingService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @PersistenceContext
    private EntityManager entityManager;

    // ==========================================
    // TEST 1: KIEM TRA LUU VA DOC CUOC HOP
    // THEO ERD MOI
    // ==========================================

    @Test
    @Transactional
    void meetingQueriesWorkWithStringIdAndLocalDateTime() {

        LocalDateTime start =
                LocalDateTime.of(2030, 7, 10, 9, 0);

        Meeting meeting = new Meeting();

        meeting.setMeetingId(
                UUID.randomUUID().toString()
        );

        meeting.setTitle("Meeting test");
        meeting.setDescription("Test meeting repository");

        meeting.setStartTime(start);
        meeting.setEndTime(start.plusHours(1));

        meeting.setOrganizerId("USER_TEST");

        meeting.setStatus("SCHEDULED");

        meeting.setIsRecurring(false);

        meeting.setCreatedAt(start.minusDays(1));
        meeting.setUpdatedAt(start.minusDays(1));

        // Luu vao database test
        Meeting saved =
                meetingRepository.saveAndFlush(meeting);

        // Xoa cache JPA de kiem tra doc lai tu DB
        entityManager.clear();

        Meeting loaded =
                meetingRepository
                        .findAllByOrderByStartTimeAsc()
                        .stream()
                        .filter(item ->
                                item.getMeetingId()
                                        .equals(saved.getMeetingId())
                        )
                        .findFirst()
                        .orElseThrow();

        assertEquals(
                saved.getMeetingId(),
                loaded.getMeetingId()
        );

        assertEquals(
                "Meeting test",
                loaded.getTitle()
        );

        assertEquals(
                start,
                loaded.getStartTime()
        );

        assertEquals(
                start.plusHours(1),
                loaded.getEndTime()
        );

        assertEquals(
                "SCHEDULED",
                loaded.getStatus()
        );
    }

    // ==========================================
    // TEST 2: KIEM TRA TRANG THAI THIET BI
    // ==========================================

    @Test
    @Transactional
    void statusAtTimeReportsAvailableBookedAndMaintenanceQuantities() {

        String room = "P-status-query";

        Equipment equipment =
                saveEquipment(room, "Test projector", 3);

        OffsetDateTime start =
                OffsetDateTime.parse(
                        "2030-05-10T09:00:00+07:00"
                );

        saveReservation(
                equipment,
                room,
                1,
                start,
                start.plusHours(1),
                EquipmentBookingStatus.BOOKED
        );

        saveReservation(
                equipment,
                room,
                1,
                start,
                start.plusHours(1),
                EquipmentBookingStatus.MAINTENANCE
        );

        EquipmentController.EquipmentStatusResponse during =
                equipmentController
                        .status(room, start.plusMinutes(30))
                        .get(0);

        assertEquals(3, during.totalQuantity());
        assertEquals(1, during.bookedQuantity());
        assertEquals(1, during.maintenanceQuantity());
        assertEquals(1, during.availableQuantity());

        assertEquals(
                List.of(
                        EquipmentAvailabilityStatus.AVAILABLE,
                        EquipmentAvailabilityStatus.BOOKED,
                        EquipmentAvailabilityStatus.MAINTENANCE
                ),
                during.statuses()
        );

        EquipmentController.EquipmentStatusResponse after =
                equipmentController
                        .status(room, start.plusHours(1))
                        .get(0);

        assertEquals(3, after.totalQuantity());
        assertEquals(3, after.availableQuantity());

        assertTrue(
                after.statuses().contains(
                        EquipmentAvailabilityStatus.AVAILABLE
                )
        );

        assertEquals(1, after.statuses().size());
    }

    // ==========================================
    // TEST 3: KIEM TRA DAT THIET BI TRUNG LICH
    // ==========================================

    @Test
    @Transactional
    void bookingRejectsOverlappingRequestsBeyondInventory() {

        String room = "P-booking-conflict";

        Equipment equipment =
                saveEquipment(room, "Test display", 2);

        OffsetDateTime start =
                OffsetDateTime.parse(
                        "2030-06-10T14:00:00+07:00"
                );

        saveReservation(
                equipment,
                room,
                2,
                start,
                start.plusHours(2),
                EquipmentBookingStatus.BOOKED
        );

        EquipmentController.EquipmentBookingRequest request =
                new EquipmentController.EquipmentBookingRequest(
                        equipment.getEquipmentId(),
                        null,
                        room,
                        1,
                        start.plusHours(1),
                        start.plusHours(3)
                );

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> equipmentController
                                .book(request)
                );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );
    }

    // ==========================================
    // HAM HO TRO TAO THIET BI
    // ==========================================

    private Equipment saveEquipment(
            String room,
            String name,
            int quantity) {

        Equipment equipment = new Equipment();

        equipment.setEquipmentId(
                "EQ-" + UUID.randomUUID()
        );

        equipment.setRoomId(room);
        equipment.setEquipmentName(name);
        equipment.setType("Test");
        equipment.setTotalQuantity(quantity);
        equipment.setStatus("AVAILABLE");

        return equipmentRepository.saveAndFlush(
                equipment
        );
    }

    // ==========================================
    // HAM HO TRO TAO LICH DAT THIET BI
    // ==========================================

    private void saveReservation(
            Equipment equipment,
            String room,
            int quantity,
            OffsetDateTime start,
            OffsetDateTime end,
            EquipmentBookingStatus status) {

        EquipmentBooking reservation =
                new EquipmentBooking();

        reservation.setEquipment(equipment);
        reservation.setRoom(room);
        reservation.setQuantity(quantity);
        reservation.setStartTime(start);
        reservation.setEndTime(end);
        reservation.setStatus(status);

        bookingRepository.save(reservation);
    }

    @Test
    @Transactional
    void roomAvailabilityDetectsBusyAndAvailableRooms() {
        Room roomA = new Room();
        roomA.setRoomId("P-TEST-A");
        roomA.setRoomName("Phòng A");
        roomA.setCapacity(10);
        roomA.setStatus("AVAILABLE");
        roomRepository.saveAndFlush(roomA);

        Room roomB = new Room();
        roomB.setRoomId("P-TEST-B");
        roomB.setRoomName("Phòng B");
        roomB.setCapacity(20);
        roomB.setStatus("AVAILABLE");
        roomRepository.saveAndFlush(roomB);

        OffsetDateTime start = OffsetDateTime.parse("2030-08-10T14:00:00+07:00");
        OffsetDateTime end = start.plusHours(2);

        Meeting meeting = new Meeting();
        meeting.setMeetingId("MTG-ROOM-A");
        meeting.setTitle("Meeting Room A");
        meeting.setRoomId(roomA.getRoomId());
        meeting.setStartTime(start.toLocalDateTime());
        meeting.setEndTime(end.toLocalDateTime());
        meeting.setOrganizerId("USR-1");
        meeting.setStatus(MeetingStatus.SCHEDULED);
        meeting.setCreatedAt(start.minusDays(1).toLocalDateTime());
        meeting.setUpdatedAt(start.minusDays(1).toLocalDateTime());
        meetingRepository.saveAndFlush(meeting);

        List<AvailableRoomResponse> available = roomAvailabilityService.getAvailableRooms(start, end, null);
        List<String> roomIds = available.stream().map(AvailableRoomResponse::roomId).toList();
        assertTrue(roomIds.contains("P-TEST-B"));
        assertFalse(roomIds.contains("P-TEST-A"));
    }

    @Test
    @Transactional
    void roomBookingAndCancelWorkflow() {
        Room room = new Room();
        room.setRoomId("P-TEST-BOOK");
        room.setRoomName("Phòng Đặt");
        room.setCapacity(15);
        room.setStatus("AVAILABLE");
        roomRepository.saveAndFlush(room);

        Role role = roleRepository.findById("ROLE_ADMIN").orElseGet(() ->
                roleRepository.saveAndFlush(new Role("ROLE_ADMIN", "ADMIN")));
        userRepository.findById("USR-1").orElseGet(() -> {
            User u = new User();
            u.setUserId("USR-1");
            u.setEmail("usr1@example.com");
            u.setFullName("User One");
            u.setPasswordHash("hash");
            u.setRole(role);
            u.setCreatedAt(LocalDateTime.now());
            u.setUpdatedAt(LocalDateTime.now());
            return userRepository.saveAndFlush(u);
        });

        OffsetDateTime start = OffsetDateTime.parse("2030-09-10T10:00:00+07:00");
        OffsetDateTime end = start.plusHours(1);

        Meeting meeting = new Meeting();
        meeting.setMeetingId("MTG-BOOK-TEST");
        meeting.setTitle("Meeting Book Test");
        meeting.setStartTime(start.toLocalDateTime());
        meeting.setEndTime(end.toLocalDateTime());
        meeting.setOrganizerId("USR-1");
        meeting.setStatus(MeetingStatus.SCHEDULED);
        meeting.setCreatedAt(start.minusDays(1).toLocalDateTime());
        meeting.setUpdatedAt(start.minusDays(1).toLocalDateTime());
        meetingRepository.saveAndFlush(meeting);

        RoomBookingResponse bookRes = roomBookingService.bookRoom(new RoomBookingRequest(meeting.getMeetingId(), room.getRoomId()));
        assertEquals(room.getRoomId(), bookRes.getRoomId());

        Meeting afterBook = meetingRepository.findById(meeting.getMeetingId()).orElseThrow();
        assertEquals(room.getRoomId(), afterBook.getRoomId());

        RoomBookingResponse cancelRes = roomBookingService.cancelRoomBooking(meeting.getMeetingId());
        assertEquals("Room booking cancelled successfully", cancelRes.getMessage());

        Meeting afterCancel = meetingRepository.findById(meeting.getMeetingId()).orElseThrow();
        assertNull(afterCancel.getRoomId());
    }

    @Test
    @Transactional
    void roomAvailabilityOverviewReturnsRealCountsAndDisabledReasons() {
        OffsetDateTime start = OffsetDateTime.parse("2030-10-15T10:00:00+07:00");
        OffsetDateTime end = start.plusHours(1);

        // Tạo cuộc họp trên phòng P.101 để tạo xung đột lịch
        Meeting meeting = new Meeting();
        meeting.setMeetingId("MTG-US07-CONFLICT");
        meeting.setTitle("Họp dự án Alpha");
        meeting.setRoomId("P.101");
        meeting.setStartTime(start.toLocalDateTime());
        meeting.setEndTime(end.toLocalDateTime());
        meeting.setOrganizerId("USR-1");
        meeting.setStatus(MeetingStatus.SCHEDULED);
        meeting.setCreatedAt(start.minusDays(1).toLocalDateTime());
        meeting.setUpdatedAt(start.minusDays(1).toLocalDateTime());
        meetingRepository.saveAndFlush(meeting);

        // Truy vấn tổng quan phòng trống trong khoảng thời gian trùng lịch
        OffsetDateTime checkStart = start.plusMinutes(15);
        OffsetDateTime checkEnd = start.plusMinutes(45);
        RoomAvailabilitySummaryResponse summary = roomAvailabilityService.getRoomAvailabilityOverview(checkStart, checkEnd, null);

        assertTrue(summary.totalRooms() >= 6, "Tổng số phòng phải ít nhất 6");
        assertTrue(summary.busyRoomsCount() >= 1, "Phải có ít nhất 1 phòng bận");
        assertTrue(summary.maintenanceRoomsCount() >= 2, "Phải có ít nhất 2 phòng bảo trì/ngừng sử dụng");

        // Tìm trạng thái chi tiết của từng phòng
        RoomScheduleStatusResponse p101 = summary.rooms().stream()
                .filter(r -> "P.101".equals(r.roomId()))
                .findFirst()
                .orElseThrow();
        assertFalse(p101.isAvailable(), "P.101 đã trùng lịch nên không khả dụng");
        assertEquals("Đã có lịch họp trùng giờ", p101.reason());

        RoomScheduleStatusResponse p301 = summary.rooms().stream()
                .filter(r -> "P.301".equals(r.roomId()))
                .findFirst()
                .orElseThrow();
        assertFalse(p301.isAvailable(), "P.301 đang bảo trì nên không khả dụng");
        assertEquals("Phòng đang bảo trì", p301.reason());

        RoomScheduleStatusResponse p302 = summary.rooms().stream()
                .filter(r -> "P.302".equals(r.roomId()))
                .findFirst()
                .orElseThrow();
        assertFalse(p302.isAvailable(), "P.302 ngừng sử dụng nên không khả dụng");
        assertEquals("Phòng ngừng sử dụng", p302.reason());

        RoomScheduleStatusResponse p102 = summary.rooms().stream()
                .filter(r -> "P.102".equals(r.roomId()))
                .findFirst()
                .orElseThrow();
        assertTrue(p102.isAvailable(), "P.102 phải khả dụng và còn trống");
        assertNull(p102.reason());
    }

    @Test
    @Transactional
    void roomAvailabilityOverviewWithMinCapacity_ExceedsAllRoomsReturnsZero() {
        OffsetDateTime start = OffsetDateTime.parse("2030-11-20T08:00:00+07:00");
        OffsetDateTime end = start.plusHours(2);

        // Yêu cầu sức chứa 500 người (vượt quá mọi phòng)
        RoomAvailabilitySummaryResponse summary = roomAvailabilityService.getRoomAvailabilityOverview(start, end, 500);

        assertEquals(0, summary.availableRoomsCount(), "Không có phòng nào đủ 500 người");
        boolean allUnavailable = summary.rooms().stream().noneMatch(RoomScheduleStatusResponse::isAvailable);
        assertTrue(allUnavailable, "Tất cả các phòng phải không khả dụng khi hết phòng phù hợp");
    }
}
