package com.ttcs.meetingmanagement.roombooking;

import com.ttcs.meetingmanagement.dto.RoomBookingRequest;
import com.ttcs.meetingmanagement.dto.RoomBookingResponse;
import com.ttcs.meetingmanagement.model.Meeting;
import com.ttcs.meetingmanagement.model.Role;
import com.ttcs.meetingmanagement.model.User;
import com.ttcs.meetingmanagement.repository.MeetingRepository;
import com.ttcs.meetingmanagement.repository.UserRepository;
import com.ttcs.meetingmanagement.room.Room;
import com.ttcs.meetingmanagement.room.RoomRepository;
import com.ttcs.meetingmanagement.service.RoomBookingPermissionService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class US08RoomBookingServiceTest {

    @Mock
    private MeetingRepository meetingRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private RoomBookingPermissionService permissionService;

    @Mock
    private UserRepository userRepository;

    private RoomBookingService roomBookingService;

    private Meeting validMeeting;
    private Room validRoom;

    @BeforeEach
    void setUp() {
        roomBookingService = new RoomBookingService(
                meetingRepository,
                roomRepository,
                permissionService,
                userRepository
        );

        validMeeting = new Meeting();
        validMeeting.setMeetingId("MEET-01");
        validMeeting.setOrganizerId("USER-PM");
        validMeeting.setTitle("Sprint Planning");
        validMeeting.setStatus("SCHEDULED");
        validMeeting.setStartTime(LocalDateTime.of(2026, 11, 1, 9, 0));
        validMeeting.setEndTime(LocalDateTime.of(2026, 11, 1, 10, 0));

        validRoom = new Room();
        validRoom.setRoomId("ROOM-01");
        validRoom.setRoomName("Alpha Room");
        validRoom.setCapacity(10);
        validRoom.setStatus("AVAILABLE");
    }

    @Test
    @DisplayName("TC01: Dat phong hop le -> thanh cong")
    void tc01_bookRoom_valid_success() {
        when(meetingRepository.findByIdForUpdate("MEET-01")).thenReturn(Optional.of(validMeeting));
        when(roomRepository.findLockedById("ROOM-01")).thenReturn(Optional.of(validRoom));
        when(meetingRepository.countRoomConflicts(eq("ROOM-01"), eq("MEET-01"), any(), any())).thenReturn(0L);
        when(meetingRepository.saveAndFlush(any(Meeting.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RoomBookingRequest req = new RoomBookingRequest();
        req.setMeetingId("MEET-01");
        req.setRoomId("ROOM-01");

        RoomBookingResponse resp = roomBookingService.bookRoom(req);

        assertNotNull(resp);
        assertEquals("MEET-01", resp.getMeetingId());
        assertEquals("ROOM-01", resp.getRoomId());
        assertEquals("Room booked successfully", resp.getMessage());
        assertEquals("ROOM-01", validMeeting.getRoomId());
        verify(permissionService).assertCanBook("USER-PM", "ROOM-01");
        verify(meetingRepository).saveAndFlush(validMeeting);
    }

    @Test
    @DisplayName("TC02: Meeting khong ton tai -> 404")
    void tc02_bookRoom_meetingNotFound_throws404() {
        when(meetingRepository.findByIdForUpdate("MEET-NONE")).thenReturn(Optional.empty());

        RoomBookingRequest req = new RoomBookingRequest();
        req.setMeetingId("MEET-NONE");
        req.setRoomId("ROOM-01");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> roomBookingService.bookRoom(req));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("Meeting not found", ex.getReason());
    }

    @Test
    @DisplayName("TC03: Room khong ton tai -> 404")
    void tc03_bookRoom_roomNotFound_throws404() {
        when(meetingRepository.findByIdForUpdate("MEET-01")).thenReturn(Optional.of(validMeeting));
        when(roomRepository.findLockedById("ROOM-NONE")).thenReturn(Optional.empty());

        RoomBookingRequest req = new RoomBookingRequest();
        req.setMeetingId("MEET-01");
        req.setRoomId("ROOM-NONE");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> roomBookingService.bookRoom(req));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("Room not found", ex.getReason());
    }

    @Test
    @DisplayName("TC04: Thieu meetingId hoac roomId -> 400")
    void tc04_bookRoom_missingIds_throws400() {
        RoomBookingRequest reqNull = null;
        assertThrows(ResponseStatusException.class, () -> roomBookingService.bookRoom(reqNull));

        RoomBookingRequest reqNoMeeting = new RoomBookingRequest();
        reqNoMeeting.setRoomId("ROOM-01");
        ResponseStatusException ex1 = assertThrows(ResponseStatusException.class,
                () -> roomBookingService.bookRoom(reqNoMeeting));
        assertEquals(HttpStatus.BAD_REQUEST, ex1.getStatusCode());

        RoomBookingRequest reqNoRoom = new RoomBookingRequest();
        reqNoRoom.setMeetingId("MEET-01");
        reqNoRoom.setRoomId("   ");
        ResponseStatusException ex2 = assertThrows(ResponseStatusException.class,
                () -> roomBookingService.bookRoom(reqNoRoom));
        assertEquals(HttpStatus.BAD_REQUEST, ex2.getStatusCode());
    }

    @Test
    @DisplayName("TC05: Meeting CANCELLED -> tu choi 400")
    void tc05_bookRoom_meetingCancelled_throws400() {
        validMeeting.setStatus("CANCELLED");
        when(meetingRepository.findByIdForUpdate("MEET-01")).thenReturn(Optional.of(validMeeting));

        RoomBookingRequest req = new RoomBookingRequest();
        req.setMeetingId("MEET-01");
        req.setRoomId("ROOM-01");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> roomBookingService.bookRoom(req));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("cancelled"));
    }

    @Test
    @DisplayName("TC06: Thoi gian meeting khong hop le -> 400")
    void tc06_bookRoom_invalidMeetingTime_throws400() {
        validMeeting.setEndTime(validMeeting.getStartTime().minusMinutes(30));
        when(meetingRepository.findByIdForUpdate("MEET-01")).thenReturn(Optional.of(validMeeting));

        RoomBookingRequest req = new RoomBookingRequest();
        req.setMeetingId("MEET-01");
        req.setRoomId("ROOM-01");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> roomBookingService.bookRoom(req));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Invalid meeting time", ex.getReason());
    }

    @Test
    @DisplayName("TC07: Room bao tri / ngung hoat dong -> bi tu choi 400")
    void tc07_bookRoom_roomMaintenance_throws400() {
        validRoom.setStatus("MAINTENANCE");
        when(meetingRepository.findByIdForUpdate("MEET-01")).thenReturn(Optional.of(validMeeting));
        when(roomRepository.findLockedById("ROOM-01")).thenReturn(Optional.of(validRoom));

        RoomBookingRequest req = new RoomBookingRequest();
        req.setMeetingId("MEET-01");
        req.setRoomId("ROOM-01");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> roomBookingService.bookRoom(req));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("not available"));
    }

    @Test
    @DisplayName("TC08: Room da duoc dat 09:00-10:00, request 09:30-10:30 -> 409 Conflict")
    void tc08_bookRoom_timeOverlapConflict_throws409() {
        when(meetingRepository.findByIdForUpdate("MEET-01")).thenReturn(Optional.of(validMeeting));
        when(roomRepository.findLockedById("ROOM-01")).thenReturn(Optional.of(validRoom));
        when(meetingRepository.countRoomConflicts(eq("ROOM-01"), eq("MEET-01"), any(), any())).thenReturn(1L);

        RoomBookingRequest req = new RoomBookingRequest();
        req.setMeetingId("MEET-01");
        req.setRoomId("ROOM-01");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> roomBookingService.bookRoom(req));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals("Room already booked in this time range", ex.getReason());
    }

    @Test
    @DisplayName("TC09: Request 10:00-11:00 sau lich 09:00-10:00 -> thanh cong")
    void tc09_bookRoom_adjacentTimeRange_success() {
        Meeting meeting2 = new Meeting();
        meeting2.setMeetingId("MEET-02");
        meeting2.setOrganizerId("USER-PM");
        meeting2.setStatus("SCHEDULED");
        meeting2.setStartTime(LocalDateTime.of(2026, 11, 1, 10, 0));
        meeting2.setEndTime(LocalDateTime.of(2026, 11, 1, 11, 0));

        when(meetingRepository.findByIdForUpdate("MEET-02")).thenReturn(Optional.of(meeting2));
        when(roomRepository.findLockedById("ROOM-01")).thenReturn(Optional.of(validRoom));
        when(meetingRepository.countRoomConflicts(eq("ROOM-01"), eq("MEET-02"), eq(meeting2.getStartTime()), eq(meeting2.getEndTime()))).thenReturn(0L);
        when(meetingRepository.saveAndFlush(any(Meeting.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RoomBookingRequest req = new RoomBookingRequest();
        req.setMeetingId("MEET-02");
        req.setRoomId("ROOM-01");

        RoomBookingResponse resp = roomBookingService.bookRoom(req);
        assertNotNull(resp);
        assertEquals("ROOM-01", resp.getRoomId());
        assertEquals("Room booked successfully", resp.getMessage());
    }

    @Test
    @DisplayName("TC10: Meeting CANCELLED khong chan mot booking moi")
    void tc10_bookRoom_cancelledMeetingDoesNotBlock() {
        when(meetingRepository.findByIdForUpdate("MEET-01")).thenReturn(Optional.of(validMeeting));
        when(roomRepository.findLockedById("ROOM-01")).thenReturn(Optional.of(validRoom));
        when(meetingRepository.countRoomConflicts(eq("ROOM-01"), eq("MEET-01"), any(), any())).thenReturn(0L);
        when(meetingRepository.saveAndFlush(any(Meeting.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RoomBookingRequest req = new RoomBookingRequest();
        req.setMeetingId("MEET-01");
        req.setRoomId("ROOM-01");

        RoomBookingResponse resp = roomBookingService.bookRoom(req);
        assertNotNull(resp);
        assertEquals("ROOM-01", resp.getRoomId());
    }

    @Test
    @DisplayName("TC18: Member khong du quyen dat phong -> 403 Forbidden")
    void tc18_bookRoom_memberRole_throws403() {
        when(meetingRepository.findByIdForUpdate("MEET-01")).thenReturn(Optional.of(validMeeting));

        User memberUser = new User();
        memberUser.setUserId("USER-MEMBER");
        Role memberRole = new Role();
        memberRole.setRoleId("ROLE_MEMBER");
        memberRole.setRoleName("Người tham dự");
        memberUser.setRole(memberRole);

        when(userRepository.findWithRole("USER-MEMBER")).thenReturn(Optional.of(memberUser));

        RoomBookingRequest req = new RoomBookingRequest();
        req.setMeetingId("MEET-01");
        req.setRoomId("ROOM-01");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> roomBookingService.bookRoom(req, "USER-MEMBER"));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertTrue(ex.getReason().contains("permission"));
    }

    @Test
    @DisplayName("TC19: User gia mao meetingId cua nguoi khac -> 403 Forbidden")
    void tc19_bookRoom_forgedMeetingId_throws403() {
        when(meetingRepository.findByIdForUpdate("MEET-01")).thenReturn(Optional.of(validMeeting));

        User otherPm = new User();
        otherPm.setUserId("USER-OTHER-PM");
        Role pmRole = new Role();
        pmRole.setRoleId("ROLE_PM");
        pmRole.setRoleName("Người đặt lịch");
        otherPm.setRole(pmRole);

        when(userRepository.findWithRole("USER-OTHER-PM")).thenReturn(Optional.of(otherPm));

        RoomBookingRequest req = new RoomBookingRequest();
        req.setMeetingId("MEET-01");
        req.setRoomId("ROOM-01");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> roomBookingService.bookRoom(req, "USER-OTHER-PM"));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Only the organizer or an administrator"));
    }

    @Test
    @DisplayName("TC21: Meeting da co phong va request retry khong gay ra loi")
    void tc21_bookRoom_idempotentRetry_success() {
        validMeeting.setRoomId("ROOM-01");
        when(meetingRepository.findByIdForUpdate("MEET-01")).thenReturn(Optional.of(validMeeting));
        when(roomRepository.findLockedById("ROOM-01")).thenReturn(Optional.of(validRoom));
        when(meetingRepository.countRoomConflicts(eq("ROOM-01"), eq("MEET-01"), any(), any())).thenReturn(0L);
        when(meetingRepository.saveAndFlush(any(Meeting.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RoomBookingRequest req = new RoomBookingRequest();
        req.setMeetingId("MEET-01");
        req.setRoomId("ROOM-01");

        RoomBookingResponse resp = roomBookingService.bookRoom(req);
        assertNotNull(resp);
        assertEquals("ROOM-01", resp.getRoomId());
        assertEquals("Room booked successfully", resp.getMessage());
    }

    @Test
    @DisplayName("TC22: Doi phong that bai vi phong moi trung lich khong lam mat booking cu")
    void tc22_bookRoom_changeRoomConflict_preservesOldRoom() {
        validMeeting.setRoomId("ROOM-OLD");
        when(meetingRepository.findByIdForUpdate("MEET-01")).thenReturn(Optional.of(validMeeting));

        Room newRoom = new Room();
        newRoom.setRoomId("ROOM-NEW");
        newRoom.setRoomName("Beta Room");
        newRoom.setStatus("AVAILABLE");
        newRoom.setCapacity(15);
        when(roomRepository.findLockedById("ROOM-NEW")).thenReturn(Optional.of(newRoom));

        when(meetingRepository.countRoomConflicts(eq("ROOM-NEW"), eq("MEET-01"), any(), any())).thenReturn(1L);

        RoomBookingRequest req = new RoomBookingRequest();
        req.setMeetingId("MEET-01");
        req.setRoomId("ROOM-NEW");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> roomBookingService.bookRoom(req));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals("ROOM-OLD", validMeeting.getRoomId());
        verify(meetingRepository, never()).saveAndFlush(any());
    }
}