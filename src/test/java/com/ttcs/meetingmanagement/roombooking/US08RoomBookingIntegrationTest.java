package com.ttcs.meetingmanagement.roombooking;

import com.ttcs.meetingmanagement.dto.AvailableRoomResponse;
import com.ttcs.meetingmanagement.dto.CreateMeetingRequest;
import com.ttcs.meetingmanagement.dto.RoomBookingRequest;
import com.ttcs.meetingmanagement.dto.RoomBookingResponse;
import com.ttcs.meetingmanagement.exception.MeetingException;
import com.ttcs.meetingmanagement.model.Meeting;
import com.ttcs.meetingmanagement.model.RoomRestriction;
import com.ttcs.meetingmanagement.model.User;
import com.ttcs.meetingmanagement.repository.MeetingRepository;
import com.ttcs.meetingmanagement.repository.UserRepository;
import com.ttcs.meetingmanagement.room.Room;
import com.ttcs.meetingmanagement.room.RoomAvailabilityService;
import com.ttcs.meetingmanagement.room.RoomRepository;
import com.ttcs.meetingmanagement.service.MeetingService;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = { "spring.mail.host=localhost" })
@Transactional
class US08RoomBookingIntegrationTest {

    @Autowired
    private RoomBookingService roomBookingService;

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoomAvailabilityService roomAvailabilityService;

    @Autowired
    private MeetingService meetingService;

    @Autowired
    private EntityManager em;

    private final String userId = "USER-US08-ORG";
    private final String otherUserId = "USER-US08-OTHER";
    private final String roomId = "ROOM-US08-A";
    private final String restrictedRoomId = "ROOM-US08-RESTRICTED";

    @BeforeEach
    void setupFixtures() {
        if (!userRepository.existsById(userId)) {
            User u = new User();
            u.setUserId(userId);
            u.setDepartmentId("DEPT-IT");
            u.setStatus("ACTIVE");
            userRepository.saveAndFlush(u);
        }

        if (!userRepository.existsById(otherUserId)) {
            User u = new User();
            u.setUserId(otherUserId);
            u.setDepartmentId("DEPT-HR");
            u.setStatus("ACTIVE");
            userRepository.saveAndFlush(u);
        }

        if (!roomRepository.existsById(roomId)) {
            Room r = new Room();
            r.setRoomId(roomId);
            r.setRoomName("US08 Alpha Room");
            r.setCapacity(12);
            r.setStatus("AVAILABLE");
            roomRepository.saveAndFlush(r);
        }

        if (!roomRepository.existsById(restrictedRoomId)) {
            Room r = new Room();
            r.setRoomId(restrictedRoomId);
            r.setRoomName("US08 VIP Restricted");
            r.setCapacity(25);
            r.setStatus("AVAILABLE");
            roomRepository.saveAndFlush(r);
        }
    }

    private Meeting createTestMeeting(String meetingId, String organizer, LocalDateTime start, LocalDateTime end) {
        Meeting m = new Meeting();
        m.setMeetingId(meetingId);
        m.setOrganizerId(organizer);
        m.setTitle("Test Meeting " + meetingId);
        m.setStartTime(start);
        m.setEndTime(end);
        m.setStatus("SCHEDULED");
        return meetingRepository.saveAndFlush(m);
    }

    @Test
    @DisplayName("TC13: Booking thanh cong phai duoc luu thuc su trong meeting.room_id")
    void tc13_bookingPersistedInDatabase() {
        LocalDateTime start = LocalDateTime.of(2026, 12, 1, 9, 0);
        LocalDateTime end = LocalDateTime.of(2026, 12, 1, 10, 0);
        createTestMeeting("MEET-TC13", userId, start, end);

        RoomBookingRequest req = new RoomBookingRequest();
        req.setMeetingId("MEET-TC13");
        req.setRoomId(roomId);

        RoomBookingResponse resp = roomBookingService.bookRoom(req);
        assertNotNull(resp);
        assertEquals(roomId, resp.getRoomId());

        em.clear();

        Meeting persisted = meetingRepository.findById("MEET-TC13").orElseThrow();
        assertEquals(roomId, persisted.getRoomId());
        assertNotNull(persisted.getUpdatedAt());
    }

    @Test
    @DisplayName("TC14: Transaction that bai khong de lai du lieu booking mot phan")
    void tc14_transactionFailureRollsBackCleanly() {
        LocalDateTime start = LocalDateTime.of(2026, 12, 2, 9, 0);
        LocalDateTime end = LocalDateTime.of(2026, 12, 2, 10, 0);
        createTestMeeting("MEET-TC14-1", userId, start, end);

        // Dat phong lan 1 thanh cong
        RoomBookingRequest req1 = new RoomBookingRequest();
        req1.setMeetingId("MEET-TC14-1");
        req1.setRoomId(roomId);
        roomBookingService.bookRoom(req1);

        // Cuoc hop thu 2 trung gio
        createTestMeeting("MEET-TC14-2", userId, start.plusMinutes(15), end.plusMinutes(15));
        RoomBookingRequest req2 = new RoomBookingRequest();
        req2.setMeetingId("MEET-TC14-2");
        req2.setRoomId(roomId);

        assertThrows(ResponseStatusException.class, () -> roomBookingService.bookRoom(req2));

        em.clear();

        Meeting m2 = meetingRepository.findById("MEET-TC14-2").orElseThrow();
        assertNull(m2.getRoomId()); // Room khong bi gan do rollback
    }

    @Test
    @DisplayName("TC15: Sau khi booking, GET /api/rooms/available khong tra ve phong do cho khung gio trung")
    void tc15_us07Sync_bookedRoomExcludedFromAvailable() {
        LocalDateTime start = LocalDateTime.of(2026, 12, 3, 14, 0);
        LocalDateTime end = LocalDateTime.of(2026, 12, 3, 15, 30);
        createTestMeeting("MEET-TC15", userId, start, end);

        // Truoc khi dat: phong co trong danh sach available
        List<AvailableRoomResponse> before = roomAvailabilityService.getAvailableRooms(start, end, null);
        assertTrue(before.stream().anyMatch(r -> roomId.equals(r.roomId())));

        // Dat phong
        RoomBookingRequest req = new RoomBookingRequest();
        req.setMeetingId("MEET-TC15");
        req.setRoomId(roomId);
        roomBookingService.bookRoom(req);

        // Sau khi dat: phong bi loai khoi danh sach available trong khung gio giao nhau
        List<AvailableRoomResponse> afterOverlap = roomAvailabilityService.getAvailableRooms(
                start.plusMinutes(15),
                end.plusMinutes(15),
                null
        );
        assertFalse(afterOverlap.stream().anyMatch(r -> roomId.equals(r.roomId())));
    }

    @Test
    @DisplayName("TC16: Truy van ngoai khung gio da dat van tra ve phong neu phong hop le")
    void tc16_us07Sync_nonOverlappingQueryReturnsRoom() {
        LocalDateTime start = LocalDateTime.of(2026, 12, 4, 9, 0);
        LocalDateTime end = LocalDateTime.of(2026, 12, 4, 10, 0);
        createTestMeeting("MEET-TC16", userId, start, end);

        RoomBookingRequest req = new RoomBookingRequest();
        req.setMeetingId("MEET-TC16");
        req.setRoomId(roomId);
        roomBookingService.bookRoom(req);

        // Truy van khung gio sau do: 10:00 - 11:00 (khong giao nhau)
        List<AvailableRoomResponse> after = roomAvailabilityService.getAvailableRooms(
                LocalDateTime.of(2026, 12, 4, 10, 0),
                LocalDateTime.of(2026, 12, 4, 11, 0),
                null
        );
        assertTrue(after.stream().anyMatch(r -> roomId.equals(r.roomId())));
    }

    @Test
    @DisplayName("TC17: Huy phong qua US09 -> phong kha dung tro lai")
    void tc17_us09Cancel_roomBecomesAvailableAgain() {
        LocalDateTime start = LocalDateTime.of(2026, 12, 5, 8, 0);
        LocalDateTime end = LocalDateTime.of(2026, 12, 5, 9, 0);
        createTestMeeting("MEET-TC17", userId, start, end);

        RoomBookingRequest req = new RoomBookingRequest();
        req.setMeetingId("MEET-TC17");
        req.setRoomId(roomId);
        roomBookingService.bookRoom(req);

        // US09 Huy dat phong
        RoomBookingResponse cancelResp = roomBookingService.cancelRoomBooking("MEET-TC17");
        assertEquals("Room booking cancelled successfully", cancelResp.getMessage());

        em.clear();

        Meeting m = meetingRepository.findById("MEET-TC17").orElseThrow();
        assertNull(m.getRoomId());

        // Kiem tra US07: phong da kha dung tro lai trong khung gio nay
        List<AvailableRoomResponse> avail = roomAvailabilityService.getAvailableRooms(start, end, null);
        assertTrue(avail.stream().anyMatch(r -> roomId.equals(r.roomId())));
    }

    @Test
    @DisplayName("TC20: Chinh sach phong han che tu US21 van duoc ap dung")
    void tc20_us21RoomRestrictionEnforced() {
        // Thiet lap phong gioi han chi danh cho DEPT-IT
        RoomRestriction rr = new RoomRestriction();
        rr.roomId = restrictedRoomId;
        rr.departmentId = "DEPT-IT";
        em.persist(rr);
        em.flush();

        LocalDateTime start = LocalDateTime.of(2026, 12, 6, 9, 0);
        LocalDateTime end = LocalDateTime.of(2026, 12, 6, 10, 0);

        // User thuoc DEPT-IT: thanh cong
        createTestMeeting("MEET-TC20-IT", userId, start, end);
        RoomBookingRequest reqIt = new RoomBookingRequest();
        reqIt.setMeetingId("MEET-TC20-IT");
        reqIt.setRoomId(restrictedRoomId);
        assertDoesNotThrow(() -> roomBookingService.bookRoom(reqIt));

        // User thuoc DEPT-HR (khong phai IT): bi tu choi 403
        createTestMeeting("MEET-TC20-HR", otherUserId, start.plusHours(2), end.plusHours(2));
        RoomBookingRequest reqHr = new RoomBookingRequest();
        reqHr.setMeetingId("MEET-TC20-HR");
        reqHr.setRoomId(restrictedRoomId);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> roomBookingService.bookRoom(reqHr));
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("TC23: Luong tao meeting (US01) khong bypass chong dat trung")
    void tc23_us01CreateMeetingCannotBypassConflictProtection() {
        LocalDateTime start = LocalDateTime.of(2026, 12, 7, 10, 0);
        LocalDateTime end = LocalDateTime.of(2026, 12, 7, 11, 0);
        createTestMeeting("MEET-TC23-1", userId, start, end);

        // Dat phong truoc qua US08
        RoomBookingRequest req = new RoomBookingRequest();
        req.setMeetingId("MEET-TC23-1");
        req.setRoomId(roomId);
        roomBookingService.bookRoom(req);

        // US01 thu tao meeting moi chon cung roomId trong khung gio giao nhau
        CreateMeetingRequest createReq = new CreateMeetingRequest();
        createReq.setOrganizerId(userId);
        createReq.setTitle("US01 Bypass Attempt");
        createReq.setRoomId(roomId);
        createReq.setStartTime(start.plusMinutes(10));
        createReq.setEndTime(end.plusMinutes(10));

        MeetingException ex = assertThrows(MeetingException.class,
                () -> meetingService.createMeeting(createReq));
        assertEquals(409, ex.getStatus().value());
    }

    @Test
    @DisplayName("TC24: Kiem tra khong gay regression cho US10 capacity va US07 availability")
    void tc24_regressionChecksPass() {
        // US10: Tim phong theo minCapacity
        List<AvailableRoomResponse> roomsForLargeGroup = roomAvailabilityService.getAvailableRooms(
                LocalDateTime.of(2026, 12, 8, 9, 0),
                LocalDateTime.of(2026, 12, 8, 10, 0),
                20
        );
        assertTrue(roomsForLargeGroup.stream().allMatch(r -> r.capacity() >= 20));
    }
}