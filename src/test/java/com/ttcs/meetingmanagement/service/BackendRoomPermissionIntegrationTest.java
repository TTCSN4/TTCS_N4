package com.ttcs.meetingmanagement.service;

import com.ttcs.meetingmanagement.dto.RoomBookingRequest;
import com.ttcs.meetingmanagement.dto.RoomBookingResponse;
import com.ttcs.meetingmanagement.model.*;
import com.ttcs.meetingmanagement.repository.MeetingRepository;
import com.ttcs.meetingmanagement.repository.UserRepository;
import com.ttcs.meetingmanagement.room.Room;
import com.ttcs.meetingmanagement.room.RoomRepository;
import com.ttcs.meetingmanagement.roombooking.RoomBookingService;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = BackendRoomPermissionIntegrationTest.TestConfig.class, properties = {
        "spring.mail.host=localhost",
        "app.mobile.jwt.secret=dGVzdC1zZWNyZXQta2V5LXdpdGgtMzItYnl0ZXMtbG9uZyEhIQ==",
        "app.mobile.jwt.issuer=test-issuer",
        "app.mobile.jwt.audience=test-audience"
})
@Transactional
class BackendRoomPermissionIntegrationTest {

    @org.springframework.boot.autoconfigure.SpringBootApplication(
            scanBasePackages = {
                    "com.ttcs.meetingmanagement.service",
                    "com.ttcs.meetingmanagement.roombooking",
                    "com.ttcs.meetingmanagement.repository",
                    "com.ttcs.meetingmanagement.room"
            }
    )
    @org.springframework.boot.persistence.autoconfigure.EntityScan(basePackages = {
            "com.ttcs.meetingmanagement"
    })
    @org.springframework.data.jpa.repository.config.EnableJpaRepositories(basePackages = {
            "com.ttcs.meetingmanagement.repository",
            "com.ttcs.meetingmanagement.room"
    })
    static class TestConfig {}

    @Autowired UserRepository users;
    @Autowired RoomRepository rooms;
    @Autowired MeetingRepository meetings;
    @Autowired RoomBookingPermissionService permissions;
    @Autowired RoomBookingService roomBookingService;
    @Autowired EntityManager em;

    private User adminUser;
    private User pmUser;
    private User memberUser;
    private Room room;
    private Meeting meeting;

    @BeforeEach
    void fixture() {
        Role adminRole = new Role();
        adminRole.setRoleId("ROLE_ADMIN");
        adminRole.setRoleName("ADMIN");
        em.persist(adminRole);

        Role pmRole = new Role();
        pmRole.setRoleId("ROLE_PM");
        pmRole.setRoleName("PROJECT_MANAGER");
        em.persist(pmRole);

        Role memberRole = new Role();
        memberRole.setRoleId("ROLE_MEMBER");
        memberRole.setRoleName("MEMBER");
        em.persist(memberRole);

        adminUser = new User();
        adminUser.userId = "ADMIN-U";
        adminUser.setRole(adminRole);
        adminUser.departmentId = "IT";
        adminUser.status = "ACTIVE";
        users.save(adminUser);

        pmUser = new User();
        pmUser.userId = "PM-U";
        pmUser.setRole(pmRole);
        pmUser.departmentId = "IT";
        pmUser.status = "ACTIVE";
        users.save(pmUser);

        memberUser = new User();
        memberUser.userId = "MEMBER-U";
        memberUser.setRole(memberRole);
        memberUser.departmentId = "IT";
        memberUser.status = "ACTIVE";
        users.save(memberUser);

        room = new Room();
        room.roomId = "PERMISSION-R";
        room.roomName = "Permission room";
        room.capacity = 10;
        room.status = "AVAILABLE";
        rooms.saveAndFlush(room);

        meeting = new Meeting();
        meeting.meetingId = "MEETING-US21";
        meeting.organizerId = adminUser.userId;
        meeting.title = "US21 Review";
        meeting.status = "SCHEDULED";
        meeting.startTime = LocalDateTime.now().plusHours(2);
        meeting.endTime = LocalDateTime.now().plusHours(3);
        meetings.saveAndFlush(meeting);

        em.flush();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(String userId) {
        var token = new UsernamePasswordAuthenticationToken(userId, "N/A", java.util.List.of());
        SecurityContextHolder.getContext().setAuthentication(token);
    }

    // ==========================================
    // 1. ADMIN: CHO PHEP DAT, SUA, HUY PHONG
    // ==========================================
    @Test
    void adminCanBookUpdateAndCancelRoom() {
        authenticate(adminUser.userId);

        // Permission service checks
        assertDoesNotThrow(() -> permissions.assertCanBook("PERMISSION-R"));
        assertDoesNotThrow(() -> permissions.assertCanUpdate("PERMISSION-R"));
        assertDoesNotThrow(() -> permissions.assertCanCancel(meeting.meetingId));

        // Room booking service execution
        RoomBookingRequest request = new RoomBookingRequest();
        request.setMeetingId(meeting.meetingId);
        request.setRoomId(room.roomId);

        RoomBookingResponse bookResponse = assertDoesNotThrow(() -> roomBookingService.bookRoom(request));
        assertNotNull(bookResponse);
        assertEquals(room.roomId, bookResponse.getRoomId());

        RoomBookingResponse updateResponse = assertDoesNotThrow(() -> roomBookingService.updateRoomBooking(meeting.meetingId, request));
        assertNotNull(updateResponse);

        RoomBookingResponse cancelResponse = assertDoesNotThrow(() -> roomBookingService.cancelRoomBooking(meeting.meetingId));
        assertNotNull(cancelResponse);
    }

    // ==========================================
    // 2. PM: CHO PHEP DAT, SUA, HUY PHONG
    // ==========================================
    @Test
    void pmCanBookUpdateAndCancelRoom() {
        authenticate(pmUser.userId);

        // Permission service checks
        assertDoesNotThrow(() -> permissions.assertCanBook("PERMISSION-R"));
        assertDoesNotThrow(() -> permissions.assertCanUpdate("PERMISSION-R"));
        assertDoesNotThrow(() -> permissions.assertCanCancel(meeting.meetingId));

        // Room booking service execution
        RoomBookingRequest request = new RoomBookingRequest();
        request.setMeetingId(meeting.meetingId);
        request.setRoomId(room.roomId);

        RoomBookingResponse bookResponse = assertDoesNotThrow(() -> roomBookingService.bookRoom(request));
        assertNotNull(bookResponse);
        assertEquals(room.roomId, bookResponse.getRoomId());

        RoomBookingResponse updateResponse = assertDoesNotThrow(() -> roomBookingService.updateRoomBooking(meeting.meetingId, request));
        assertNotNull(updateResponse);

        RoomBookingResponse cancelResponse = assertDoesNotThrow(() -> roomBookingService.cancelRoomBooking(meeting.meetingId));
        assertNotNull(cancelResponse);
    }

    // ==========================================
    // 3. MEMBER: TU CHOI 403 VOI DAT, SUA, HUY
    // ==========================================
    @Test
    void memberDenied403ForBookUpdateAndCancel() {
        authenticate(memberUser.userId);

        ResponseStatusException exBook = assertThrows(ResponseStatusException.class,
                () -> permissions.assertCanBook("PERMISSION-R"));
        assertEquals(403, exBook.getStatusCode().value());

        ResponseStatusException exUpdate = assertThrows(ResponseStatusException.class,
                () -> permissions.assertCanUpdate("PERMISSION-R"));
        assertEquals(403, exUpdate.getStatusCode().value());

        ResponseStatusException exCancel = assertThrows(ResponseStatusException.class,
                () -> permissions.assertCanCancel(meeting.meetingId));
        assertEquals(403, exCancel.getStatusCode().value());

        // RoomBookingService also rejects Member with 403
        RoomBookingRequest request = new RoomBookingRequest();
        request.setMeetingId(meeting.meetingId);
        request.setRoomId(room.roomId);

        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> roomBookingService.bookRoom(request)).getStatusCode().value());

        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> roomBookingService.updateRoomBooking(meeting.meetingId, request)).getStatusCode().value());

        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> roomBookingService.cancelRoomBooking(meeting.meetingId)).getStatusCode().value());
    }

    // ==========================================
    // 4. CHUA XAC THUC: TU CHOI 401 VOI DAT, SUA, HUY
    // ==========================================
    @Test
    void unauthenticatedDenied401ForBookUpdateAndCancel() {
        SecurityContextHolder.clearContext();

        assertEquals(401, assertThrows(ResponseStatusException.class,
                () -> permissions.assertCanBook("PERMISSION-R")).getStatusCode().value());

        assertEquals(401, assertThrows(ResponseStatusException.class,
                () -> permissions.assertCanUpdate("PERMISSION-R")).getStatusCode().value());

        assertEquals(401, assertThrows(ResponseStatusException.class,
                () -> permissions.assertCanCancel(meeting.meetingId)).getStatusCode().value());

        RoomBookingRequest request = new RoomBookingRequest();
        request.setMeetingId(meeting.meetingId);
        request.setRoomId(room.roomId);

        assertEquals(401, assertThrows(ResponseStatusException.class,
                () -> roomBookingService.bookRoom(request)).getStatusCode().value());

        assertEquals(401, assertThrows(ResponseStatusException.class,
                () -> roomBookingService.updateRoomBooking(meeting.meetingId, request)).getStatusCode().value());

        assertEquals(401, assertThrows(ResponseStatusException.class,
                () -> roomBookingService.cancelRoomBooking(meeting.meetingId)).getStatusCode().value());
    }

    // ==========================================
    // 5. KHONG CHO PHEP GIA MAO QUYEN
    // ==========================================
    @Test
    void memberCannotSpoofAdminOrPm() {
        authenticate(memberUser.userId);

        RoomBookingRequest request = new RoomBookingRequest();
        request.setMeetingId(meeting.meetingId);
        request.setRoomId(room.roomId);

        // Even though meeting.organizerId is ADMIN-U, the authenticated actor is MEMBER-U, so it must be 403
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> roomBookingService.bookRoom(request)).getStatusCode().value());
    }

    // ==========================================
    // 6. GIOI HAN PHONG (ROOM RESTRICTIONS)
    // ==========================================
    @Test
    void supportsUnrestrictedRoomsAndDepartmentScopes() {
        assertDoesNotThrow(() -> permissions.assertCanBook(adminUser.userId, "PERMISSION-R"));
        var restriction = new RoomRestriction();
        restriction.roomId = "PERMISSION-R";
        restriction.departmentId = "IT";
        em.persist(restriction);
        em.flush();
        assertDoesNotThrow(() -> permissions.assertCanBook(adminUser.userId, "PERMISSION-R"));
    }

    @Test
    void rejectsUserOutsideTheRoomAllowList() {
        var restriction = new RoomRestriction();
        restriction.roomId = "PERMISSION-R";
        restriction.userId = "OTHER";
        em.persist(restriction);
        em.flush();
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> permissions.assertCanBook(adminUser.userId, "PERMISSION-R")).getStatusCode().value());
    }
}
