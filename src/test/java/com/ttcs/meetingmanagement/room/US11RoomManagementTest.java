package com.ttcs.meetingmanagement.room;

import com.ttcs.meetingmanagement.dto.CreateMeetingRequest;
import com.ttcs.meetingmanagement.dto.CreateRoomRequest;
import com.ttcs.meetingmanagement.dto.RoomBookingRequest;
import com.ttcs.meetingmanagement.dto.UpdateRoomRequest;
import com.ttcs.meetingmanagement.exception.MeetingException;
import com.ttcs.meetingmanagement.exception.RoomException;
import com.ttcs.meetingmanagement.meeting.update.US02MeetingRequest;
import com.ttcs.meetingmanagement.meeting.update.US02MeetingService;
import com.ttcs.meetingmanagement.model.Meeting;
import com.ttcs.meetingmanagement.model.MeetingParticipant;
import com.ttcs.meetingmanagement.model.Role;
import com.ttcs.meetingmanagement.model.User;
import com.ttcs.meetingmanagement.rbac.AuthenticatedUserIdentity;
import com.ttcs.meetingmanagement.rbac.RbacAccess;
import com.ttcs.meetingmanagement.repository.EquipmentBookingRepository;
import com.ttcs.meetingmanagement.repository.MeetingParticipantRepository;
import com.ttcs.meetingmanagement.repository.MeetingRepository;
import com.ttcs.meetingmanagement.repository.RoleRepository;
import com.ttcs.meetingmanagement.repository.UserRepository;
import com.ttcs.meetingmanagement.roombooking.RoomBookingService;
import com.ttcs.meetingmanagement.service.MeetingService;
import com.ttcs.meetingmanagement.service.MeetingParticipantService;
import org.springframework.jdbc.core.JdbcTemplate;
import com.ttcs.meetingmanagement.service.RoomBookingPermissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = US11RoomManagementTest.US11TestAppConfig.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:meeting_us11_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.main.allow-bean-definition-overriding=true"
})
@Transactional
public class US11RoomManagementTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ComponentScan(basePackages = {
            "com.ttcs.meetingmanagement.room",
            "com.ttcs.meetingmanagement.rbac",
            "com.ttcs.meetingmanagement.config",
            "com.ttcs.meetingmanagement.exception"
    })
    @EntityScan(basePackages = {
            "com.ttcs.meetingmanagement.room",
            "com.ttcs.meetingmanagement.model",
            "com.ttcs.meetingmanagement.equipment"
    })
    @EnableJpaRepositories(basePackages = {
            "com.ttcs.meetingmanagement.room",
            "com.ttcs.meetingmanagement.repository"
    })
    public static class US11TestAppConfig {
        @Bean
        public RoomBookingPermissionService roomBookingPermissionService() {
            return Mockito.mock(RoomBookingPermissionService.class);
        }

        @Bean
        public EquipmentBookingRepository equipmentBookingRepository() {
            EquipmentBookingRepository repo = Mockito.mock(EquipmentBookingRepository.class);
            Mockito.when(repo.findAllByMeetingId(Mockito.anyString())).thenReturn(List.of());
            return repo;
        }

        @Bean
        public MeetingParticipantService meetingParticipantService(MeetingParticipantRepository repository, JdbcTemplate jdbcTemplate) {
            return new MeetingParticipantService(repository, jdbcTemplate);
        }

        @Bean
        public MeetingService meetingService(MeetingRepository meetingRepository, RoomRepository roomRepository) {
            return new MeetingService(meetingRepository, roomRepository);
        }

        @Bean
        public RoomBookingService roomBookingService(
                MeetingRepository meetingRepository,
                RoomRepository roomRepository,
                RoomBookingPermissionService permissionService,
                MeetingParticipantRepository participantRepository) {
            return new RoomBookingService(meetingRepository, roomRepository, permissionService, participantRepository);
        }

        @Bean
        public US02MeetingService us02MeetingService(
                MeetingRepository meetingRepository,
                RoomRepository roomRepository,
                EquipmentBookingRepository equipmentBookingRepository,
                RoomBookingPermissionService roomBookingPermissionService,
                MeetingParticipantRepository participantRepository) {
            return new US02MeetingService(
                    meetingRepository,
                    roomRepository,
                    equipmentBookingRepository,
                    roomBookingPermissionService,
                    participantRepository
            );
        }
    }

    @Autowired private WebApplicationContext context;
    @Autowired private RoomController roomController;
    @Autowired private RoomRepository roomRepository;
    @Autowired private RoomService roomService;
    @Autowired private RoomAvailabilityService roomAvailabilityService;
    @Autowired private MeetingRepository meetingRepository;
    @Autowired private MeetingParticipantRepository participantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private MeetingParticipantService meetingParticipantService;
    @Autowired private MeetingService meetingService;
    @Autowired private RoomBookingService roomBookingService;
    @Autowired private US02MeetingService us02MeetingService;
    @Autowired private RbacAccess rbacAccess;

    private MockMvc mockMvc;
    private Role adminRole;
    private Role attendeeRole;
    private User adminUser;
    private User attendeeUser;
    private Authentication adminAuth;
    private Authentication attendeeAuth;

    private record SimpleUserIdentity(String userId) implements AuthenticatedUserIdentity {
        @Override
        public String getUserId() {
            return userId;
        }
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        adminRole = new Role();
        adminRole.setRoleId("ROLE_ADMIN");
        adminRole.setRoleName("Admin");
        roleRepository.saveAndFlush(adminRole);

        attendeeRole = new Role();
        attendeeRole.setRoleId("ROLE_ATTENDEE");
        attendeeRole.setRoleName("Nguoi tham du");
        roleRepository.saveAndFlush(attendeeRole);

        adminUser = new User();
        adminUser.setUserId("ADMIN-01");
        adminUser.setEmail("admin@example.com");
        adminUser.setFullName("Administrator");
        adminUser.setRole(adminRole);
        adminUser.setStatus("ACTIVE");
        userRepository.saveAndFlush(adminUser);

        attendeeUser = new User();
        attendeeUser.setUserId("USER-01");
        attendeeUser.setEmail("user@example.com");
        attendeeUser.setFullName("Normal User");
        attendeeUser.setRole(attendeeRole);
        attendeeUser.setStatus("ACTIVE");
        userRepository.saveAndFlush(attendeeUser);

        adminAuth = new UsernamePasswordAuthenticationToken(
                new SimpleUserIdentity(adminUser.getUserId()),
                null,
                List.of()
        );

        attendeeAuth = new UsernamePasswordAuthenticationToken(
                new SimpleUserIdentity(attendeeUser.getUserId()),
                null,
                List.of()
        );

        SecurityContextHolder.getContext().setAuthentication(adminAuth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ==========================================
    // US11.1 - ADMIN THEM PHONG MOI
    // ==========================================

    @Test
    @DisplayName("US11.1 - Admin them phong moi thanh cong, tra ve HTTP 201")
    void test01_adminCreatesRoomSuccessfully() {
        CreateRoomRequest request = new CreateRoomRequest();
        request.setRoomId("P101");
        request.setRoomName("Phong Hop A");
        request.setCapacity(20);
        request.setLocation("Tang 1");
        request.setQrCode("QR-P101");
        request.setStatus("AVAILABLE");

        ResponseEntity<Room> response = roomController.createRoom(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("P101", response.getBody().getRoomId());
        assertEquals("Phong Hop A", response.getBody().getRoomName());
        assertEquals(20, response.getBody().getCapacity());
        assertEquals("AVAILABLE", response.getBody().getStatus());

        assertTrue(roomRepository.existsById("P101"));
        Room room = roomRepository.findById("P101").orElseThrow();
        assertEquals(20, room.getCapacity());
        assertEquals("Tang 1", room.getLocation());
    }

    @Test
    @DisplayName("US11.1 - Tu choi tao phong neu trung ma phong, tra ve HTTP 409")
    void test02_rejectDuplicateRoomId() {
        Room existing = new Room();
        existing.setRoomId("P102");
        existing.setRoomName("Phong B");
        existing.setCapacity(15);
        existing.setStatus("AVAILABLE");
        roomRepository.saveAndFlush(existing);

        CreateRoomRequest request = new CreateRoomRequest();
        request.setRoomId("P102");
        request.setRoomName("Phong Khac");
        request.setCapacity(25);
        request.setStatus("AVAILABLE");

        RoomException ex = assertThrows(RoomException.class, () -> roomController.createRoom(request));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertTrue(ex.getMessage().contains("Mã phòng đã tồn tại") || ex.getMessage().contains("Ma phong da ton tai"));
    }

    @Test
    @DisplayName("US11.1 - Tu choi tao phong neu trung ten phong, tra ve HTTP 409")
    void test03_rejectDuplicateRoomName() {
        Room existing = new Room();
        existing.setRoomId("P103");
        existing.setRoomName("Phong Hoi Nghi");
        existing.setCapacity(50);
        existing.setStatus("AVAILABLE");
        roomRepository.saveAndFlush(existing);

        CreateRoomRequest request = new CreateRoomRequest();
        request.setRoomId("P104");
        request.setRoomName("phong hoi nghi"); // Khac hoa thuong
        request.setCapacity(30);
        request.setStatus("AVAILABLE");

        RoomException ex = assertThrows(RoomException.class, () -> roomController.createRoom(request));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertTrue(ex.getMessage().contains("Tên phòng đã tồn tại") || ex.getMessage().contains("Ten phong da ton tai"));
    }

    @Test
    @DisplayName("US11.1 - Tu choi du lieu tao phong khong hop le (suc chua <= 0, trang thai sai), tra ve HTTP 400")
    void test04_rejectInvalidCreateData() {
        CreateRoomRequest badCapacity = new CreateRoomRequest();
        badCapacity.setRoomId("P105");
        badCapacity.setRoomName("Phong Test");
        badCapacity.setCapacity(0);
        badCapacity.setStatus("AVAILABLE");

        RoomException exCap = assertThrows(RoomException.class, () -> roomController.createRoom(badCapacity));
        assertEquals(HttpStatus.BAD_REQUEST, exCap.getStatus());

        CreateRoomRequest badStatus = new CreateRoomRequest();
        badStatus.setRoomId("P106");
        badStatus.setRoomName("Phong Status Sai");
        badStatus.setCapacity(10);
        badStatus.setStatus("INVALID_STATUS");

        RoomException exStatus = assertThrows(RoomException.class, () -> roomController.createRoom(badStatus));
        assertEquals(HttpStatus.BAD_REQUEST, exStatus.getStatus());
        assertTrue(exStatus.getMessage().contains("Trạng thái phòng không hợp lệ") || exStatus.getMessage().contains("Trang thai phong khong hop le"));
    }

    // ==========================================
    // US11.2, US11.3 - ADMIN SUA THONG TIN PHONG
    // ==========================================

    @Test
    @DisplayName("US11.2 & US11.3 - Admin sua thong tin phong thanh cong, giu nguyen room_id")
    void test05_adminUpdatesRoomSuccessfully() {
        Room room = new Room();
        room.setRoomId("P201");
        room.setRoomName("Phong Cu");
        room.setCapacity(15);
        room.setLocation("Tang 2");
        room.setStatus("AVAILABLE");
        roomRepository.saveAndFlush(room);

        UpdateRoomRequest update = new UpdateRoomRequest();
        update.setRoomName("Phong Moi");
        update.setCapacity(25);
        update.setLocation("Tang 3, Khu A");
        update.setStatus("MAINTENANCE");

        ResponseEntity<Room> response = roomController.updateRoom("P201", update);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("P201", response.getBody().getRoomId());
        assertEquals("Phong Moi", response.getBody().getRoomName());
        assertEquals(25, response.getBody().getCapacity());
        assertEquals("MAINTENANCE", response.getBody().getStatus());
        assertEquals("Tang 3, Khu A", response.getBody().getLocation());

        Room updated = roomRepository.findById("P201").orElseThrow();
        assertEquals("Phong Moi", updated.getRoomName());
        assertEquals(25, updated.getCapacity());
        assertEquals("MAINTENANCE", updated.getStatus());
        assertEquals("Tang 3, Khu A", updated.getLocation());
    }

    @Test
    @DisplayName("US11.2 - Tu choi cap nhat ten phong bi trung voi phong khac, tra ve HTTP 409")
    void test06_updateDuplicateRoomName_rejectedWith409() {
        Room r1 = new Room();
        r1.setRoomId("P202");
        r1.setRoomName("Phong Alpha");
        r1.setCapacity(10);
        r1.setStatus("AVAILABLE");
        roomRepository.saveAndFlush(r1);

        Room r2 = new Room();
        r2.setRoomId("P203");
        r2.setRoomName("Phong Beta");
        r2.setCapacity(20);
        r2.setStatus("AVAILABLE");
        roomRepository.saveAndFlush(r2);

        UpdateRoomRequest update = new UpdateRoomRequest();
        update.setRoomName("phong alpha"); // trung voi P202
        update.setCapacity(25);
        update.setStatus("AVAILABLE");

        RoomException ex = assertThrows(RoomException.class, () -> roomController.updateRoom("P203", update));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertTrue(ex.getMessage().contains("Tên phòng đã tồn tại") || ex.getMessage().contains("Ten phong da ton tai"));
    }

    // ==========================================
    // US11.4 - KIEM TRA SUC CHUA VOI CUOC HOP TUONG LAI
    // ==========================================

    @Test
    @DisplayName("US11.4 - Tu choi giam suc chua neu thap hon so nguoi cua cuoc hop tuong lai, tra ve HTTP 409")
    void test07_rejectCapacityReductionBelowUpcomingMeetingAttendees() {
        Room room = new Room();
        room.setRoomId("P301");
        room.setRoomName("Phong Hop 30 Nguoi");
        room.setCapacity(30);
        room.setStatus("AVAILABLE");
        roomRepository.saveAndFlush(room);

        // Tao cuoc hop tuong lai voi 25 nguoi (1 organizer + 24 participants)
        Meeting meeting = new Meeting();
        meeting.setMeetingId("MEET-301");
        meeting.setRoomId("P301");
        meeting.setOrganizerId(adminUser.getUserId());
        meeting.setTitle("Hop Chien Luoc");
        meeting.setStartTime(LocalDateTime.now().plusDays(2));
        meeting.setEndTime(LocalDateTime.now().plusDays(2).plusHours(2));
        meeting.setStatus("SCHEDULED");
        meetingRepository.saveAndFlush(meeting);

        for (int i = 1; i <= 24; i++) {
            MeetingParticipant p = new MeetingParticipant();
            p.setMeetingId("MEET-301");
            p.setUserId("PARTICIPANT-" + i);
            p.setStatus("ACCEPTED");
            participantRepository.save(p);
        }
        participantRepository.flush();

        // Giam xuong 20 -> TU CHOI (20 < 25)
        UpdateRoomRequest update = new UpdateRoomRequest();
        update.setRoomName("Phong Hop 30 Nguoi");
        update.setCapacity(20);
        update.setStatus("AVAILABLE");

        RoomException ex = assertThrows(RoomException.class, () -> roomController.updateRoom("P301", update));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertTrue(ex.getMessage().contains("Không thể giảm sức chứa xuống 20") || ex.getMessage().contains("Khong the giam suc chua xuong 20"));

        // Kiem tra suc chua cu trong DB khong bi thay doi
        Room untouched = roomRepository.findById("P301").orElseThrow();
        assertEquals(30, untouched.getCapacity());
    }

    @Test
    @DisplayName("US11.4 - Cho phep giam suc chua bang hoac lon hon so nguoi cuoc hop")
    void test08_allowCapacityReductionEqualToAttendees() {
        Room room = new Room();
        room.setRoomId("P302");
        room.setRoomName("Phong Hop 302");
        room.setCapacity(30);
        room.setStatus("AVAILABLE");
        roomRepository.saveAndFlush(room);

        Meeting meeting = new Meeting();
        meeting.setMeetingId("MEET-302");
        meeting.setRoomId("P302");
        meeting.setOrganizerId(adminUser.getUserId());
        meeting.setTitle("Hop Phong Ban");
        meeting.setStartTime(LocalDateTime.now().plusDays(1));
        meeting.setEndTime(LocalDateTime.now().plusDays(1).plusHours(1));
        meeting.setStatus("SCHEDULED");
        meetingRepository.saveAndFlush(meeting);

        for (int i = 1; i <= 24; i++) {
            MeetingParticipant p = new MeetingParticipant();
            p.setMeetingId("MEET-302");
            p.setUserId("PARTICIPANT-" + i);
            p.setStatus("ACCEPTED");
            participantRepository.save(p);
        }
        participantRepository.flush();

        // Giam xuong dung 25 -> CHO PHEP
        UpdateRoomRequest update = new UpdateRoomRequest();
        update.setRoomName("Phong Hop 302");
        update.setCapacity(25);
        update.setStatus("AVAILABLE");

        ResponseEntity<Room> response = roomController.updateRoom("P302", update);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(25, response.getBody().getCapacity());

        Room updated = roomRepository.findById("P302").orElseThrow();
        assertEquals(25, updated.getCapacity());
    }

    @Test
    @DisplayName("US11.4 - Organizer trung participant khong bi dem 2 lan")
    void test09_organizerDuplicateInParticipantsNotDoubleCounted() {
        Room room = new Room();
        room.setRoomId("P303");
        room.setRoomName("Phong 303");
        room.setCapacity(10);
        room.setStatus("AVAILABLE");
        roomRepository.saveAndFlush(room);

        Meeting meeting = new Meeting();
        meeting.setMeetingId("MEET-303");
        meeting.setRoomId("P303");
        meeting.setOrganizerId("USER-A");
        meeting.setTitle("Hop Nhom");
        meeting.setStartTime(LocalDateTime.now().plusDays(3));
        meeting.setEndTime(LocalDateTime.now().plusDays(3).plusHours(1));
        meeting.setStatus("SCHEDULED");
        meetingRepository.saveAndFlush(meeting);

        // USER-A vua la organizer vua la participant
        MeetingParticipant p1 = new MeetingParticipant();
        p1.setMeetingId("MEET-303");
        p1.setUserId("USER-A");
        p1.setStatus("ACCEPTED");
        participantRepository.save(p1);

        MeetingParticipant p2 = new MeetingParticipant();
        p2.setMeetingId("MEET-303");
        p2.setUserId("USER-B");
        p2.setStatus("ACCEPTED");
        participantRepository.save(p2);
        participantRepository.flush();

        // Tong so nguoi thuc te la 2 (USER-A va USER-B). Giam xuong 2 phai thanh cong!
        UpdateRoomRequest req2 = new UpdateRoomRequest();
        req2.setRoomName("Phong 303");
        req2.setCapacity(2);
        req2.setStatus("AVAILABLE");
        assertDoesNotThrow(() -> roomController.updateRoom("P303", req2));

        // Giam tiep xuong 1 -> Phai bi tu choi (1 < 2)!
        UpdateRoomRequest req1 = new UpdateRoomRequest();
        req1.setRoomName("Phong 303");
        req1.setCapacity(1);
        req1.setStatus("AVAILABLE");
        RoomException ex = assertThrows(RoomException.class, () -> roomController.updateRoom("P303", req1));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    @DisplayName("US11.4 - Nguoi tham du DECLINED/REMOVED hoac cuoc hop CANCELLED/da ket thuc khong gay chan sai")
    void test10_declinedAndCancelledMeetingsDoNotBlockCapacityReduction() {
        Room room = new Room();
        room.setRoomId("P304");
        room.setRoomName("Phong 304");
        room.setCapacity(50);
        room.setStatus("AVAILABLE");
        roomRepository.saveAndFlush(room);

        // 1. Cuoc hop da ket thuc trong qua khu (100 nguoi)
        Meeting pastMeeting = new Meeting();
        pastMeeting.setMeetingId("MEET-PAST");
        pastMeeting.setRoomId("P304");
        pastMeeting.setOrganizerId("ORG");
        pastMeeting.setTitle("Cuoc Hop Nam Ngoai");
        pastMeeting.setStartTime(LocalDateTime.now().minusDays(5));
        pastMeeting.setEndTime(LocalDateTime.now().minusDays(5).plusHours(2));
        pastMeeting.setStatus("COMPLETED");
        meetingRepository.saveAndFlush(pastMeeting);

        // 2. Cuoc hop da bi huy (100 nguoi)
        Meeting cancelledMeeting = new Meeting();
        cancelledMeeting.setMeetingId("MEET-CANCELLED");
        cancelledMeeting.setRoomId("P304");
        cancelledMeeting.setOrganizerId("ORG");
        cancelledMeeting.setTitle("Cuoc Hop Da Huy");
        cancelledMeeting.setStartTime(LocalDateTime.now().plusDays(5));
        cancelledMeeting.setEndTime(LocalDateTime.now().plusDays(5).plusHours(2));
        cancelledMeeting.setStatus("CANCELLED");
        meetingRepository.saveAndFlush(cancelledMeeting);

        // 3. Cuoc hop tuong lai: Organizer + 1 DECLINED + 1 REMOVED
        Meeting futureMeeting = new Meeting();
        futureMeeting.setMeetingId("MEET-FUTURE");
        futureMeeting.setRoomId("P304");
        futureMeeting.setOrganizerId("ORG");
        futureMeeting.setTitle("Cuoc Hop Tuong Lai");
        futureMeeting.setStartTime(LocalDateTime.now().plusDays(1));
        futureMeeting.setEndTime(LocalDateTime.now().plusDays(1).plusHours(1));
        futureMeeting.setStatus("SCHEDULED");
        meetingRepository.saveAndFlush(futureMeeting);

        MeetingParticipant pDeclined = new MeetingParticipant();
        pDeclined.setMeetingId("MEET-FUTURE");
        pDeclined.setUserId("USER-DECLINED");
        pDeclined.setStatus("DECLINED");
        participantRepository.save(pDeclined);

        MeetingParticipant pRemoved = new MeetingParticipant();
        pRemoved.setMeetingId("MEET-FUTURE");
        pRemoved.setUserId("USER-REMOVED");
        pRemoved.setStatus("REMOVED");
        participantRepository.save(pRemoved);
        participantRepository.flush();

        // So nguoi thuc te cuoc hop tuong lai chi co 1 (ORG). Giam xuong 5 phai thanh cong!
        UpdateRoomRequest req = new UpdateRoomRequest();
        req.setRoomName("Phong 304");
        req.setCapacity(5);
        req.setStatus("AVAILABLE");

        ResponseEntity<Room> updated = assertDoesNotThrow(() -> roomController.updateRoom("P304", req));
        assertEquals(5, updated.getBody().getCapacity());
    }

    // ==========================================
    // US11.3 & US11.5 - DONG BO TRANG THAI VOI CAC LUONG DAT PHONG
    // ==========================================

    @Test
    @DisplayName("US11.3 & US11.5 - Phong MAINTENANCE hoac INACTIVE khong the dat qua MeetingService va RoomBookingService")
    void test11_maintenanceAndInactiveRoomsCannotBeBooked() {
        Room maintRoom = new Room();
        maintRoom.setRoomId("R-MAINT");
        maintRoom.setRoomName("Phong Bao Tri");
        maintRoom.setCapacity(10);
        maintRoom.setStatus("MAINTENANCE");
        roomRepository.saveAndFlush(maintRoom);

        Room inactiveRoom = new Room();
        inactiveRoom.setRoomId("R-INACT");
        inactiveRoom.setRoomName("Phong Ngung Su Dung");
        inactiveRoom.setCapacity(10);
        inactiveRoom.setStatus("INACTIVE");
        roomRepository.saveAndFlush(inactiveRoom);

        // 1. MeetingService.createMeeting()
        CreateMeetingRequest req1 = new CreateMeetingRequest();
        req1.setOrganizerId(adminUser.getUserId());
        req1.setRoomId("R-MAINT");
        req1.setTitle("Hop Test Bao Tri");
        req1.setStartTime(LocalDateTime.now().plusDays(1));
        req1.setEndTime(LocalDateTime.now().plusDays(1).plusHours(1));

        MeetingException ex1 = assertThrows(MeetingException.class, () -> meetingService.createMeeting(req1));
        assertEquals(HttpStatus.CONFLICT, ex1.getStatus());

        // 2. RoomBookingService.bookRoom()
        Meeting m = new Meeting();
        m.setMeetingId("MEET-BOOK-TEST");
        m.setOrganizerId(adminUser.getUserId());
        m.setTitle("Test Book");
        m.setStartTime(LocalDateTime.now().plusDays(1));
        m.setEndTime(LocalDateTime.now().plusDays(1).plusHours(1));
        m.setStatus("SCHEDULED");
        meetingRepository.saveAndFlush(m);

        RoomBookingRequest bookReq = new RoomBookingRequest();
        bookReq.setMeetingId("MEET-BOOK-TEST");
        bookReq.setRoomId("R-INACT");

        ResponseStatusException ex2 = assertThrows(ResponseStatusException.class,
                () -> roomBookingService.bookRoom(bookReq));
        assertEquals(HttpStatus.CONFLICT, ex2.getStatusCode());
    }

    @Test
    @DisplayName("US11.3 & US11.5 - Phong khong kha dung khong xuat hien trong RoomAvailabilityService")
    void test12_unavailableRoomsExcludedFromAvailabilityService() {
        Room rAvail = new Room();
        rAvail.setRoomId("R-AVAIL");
        rAvail.setRoomName("Phong Hoat Dong");
        rAvail.setCapacity(20);
        rAvail.setStatus("AVAILABLE");
        roomRepository.saveAndFlush(rAvail);

        Room rMaint = new Room();
        rMaint.setRoomId("R-MAINT-2");
        rMaint.setRoomName("Phong Dang Sua");
        rMaint.setCapacity(20);
        rMaint.setStatus("MAINTENANCE");
        roomRepository.saveAndFlush(rMaint);

        LocalDateTime start = LocalDateTime.now().plusDays(1).withHour(9).withMinute(0);
        LocalDateTime end = start.plusHours(2);

        var list = roomAvailabilityService.getAvailableRooms(start, end, 10);
        assertTrue(list.stream().anyMatch(r -> "R-AVAIL".equals(r.roomId())));
        assertFalse(list.stream().anyMatch(r -> "R-MAINT-2".equals(r.roomId())));
    }

    @Test
    @DisplayName("US11.5 - US02MeetingService.update() khong cho phep chuyen sang phong dang bao tri")
    void test13_us02CannotRescheduleToMaintenanceRoom() {
        Room maintRoom = new Room();
        maintRoom.setRoomId("R-US02-MAINT");
        maintRoom.setRoomName("Phong Bao Tri US02");
        maintRoom.setCapacity(20);
        maintRoom.setStatus("MAINTENANCE");
        roomRepository.saveAndFlush(maintRoom);

        Meeting meeting = new Meeting();
        meeting.setMeetingId("MEET-US02");
        meeting.setOrganizerId(adminUser.getUserId());
        meeting.setTitle("Hop Ban Dau");
        meeting.setStartTime(LocalDateTime.now().plusDays(1));
        meeting.setEndTime(LocalDateTime.now().plusDays(1).plusHours(1));
        meeting.setStatus("SCHEDULED");
        meetingRepository.saveAndFlush(meeting);

        US02MeetingRequest req = new US02MeetingRequest(
                "Doi Lich Hop",
                "Mo ta",
                LocalDateTime.now().plusDays(2),
                LocalDateTime.now().plusDays(2).plusHours(1),
                "R-US02-MAINT"
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> us02MeetingService.update("MEET-US02", adminUser.getUserId(), req));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().toLowerCase().contains("maintenance") || ex.getReason().toLowerCase().contains("bao tri"));
    }

    // ==========================================
    // US11.6 - PHAN QUYEN ADMIN & HTTP SECURITY TEST
    // ==========================================

    @Test
    @DisplayName("US11.6 - RbacAccess xac thuc dung vai tro Admin")
    void test14_rbacAccessIdentifiesAdminCorrectly() {
        assertTrue(rbacAccess.isAdmin(adminAuth));
        assertFalse(rbacAccess.isAdmin(attendeeAuth));
        assertFalse(rbacAccess.isAdmin(null));
    }

    @Test
    @DisplayName("US11.6 - User khong phai Admin bi chan boi MockMvc/Security tra ve HTTP 403")
    void test15_nonAdminCannotManageRooms_mockMvcReturns403() throws Exception {
        String createJson = """
                {
                    "roomId": "P-ATTENDEE",
                    "roomName": "Phong Attendee",
                    "capacity": 10,
                    "status": "AVAILABLE"
                }
                """;

        // POST /api/rooms with non-admin authentication -> 403 Forbidden
        mockMvc.perform(post("/api/rooms")
                        .with(authentication(attendeeAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson))
                .andExpect(status().isForbidden());

        // PUT /api/rooms/{id} with non-admin authentication -> 403 Forbidden
        String updateJson = """
                {
                    "roomName": "Phong Update Attendee",
                    "capacity": 10,
                    "status": "AVAILABLE"
                }
                """;

        mockMvc.perform(put("/api/rooms/P-ATTENDEE")
                        .with(authentication(attendeeAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isForbidden());

        // DELETE /api/rooms/{id} with non-admin authentication -> 403 Forbidden
        mockMvc.perform(delete("/api/rooms/P-ATTENDEE")
                        .with(authentication(attendeeAuth)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("US11.6 - Anonymous bi chan boi MockMvc/Security tra ve HTTP 401")
    void test16_anonymousCannotManageRooms_mockMvcReturns401() throws Exception {
        SecurityContextHolder.clearContext();
        String createJson = """
                {
                    "roomId": "P-ANON",
                    "roomName": "Phong Anon",
                    "capacity": 10,
                    "status": "AVAILABLE"
                }
                """;

        // POST /api/rooms without authentication -> 401 Unauthorized
        mockMvc.perform(post("/api/rooms")
                        .with(anonymous())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson))
                .andExpect(status().isUnauthorized());

        // PUT /api/rooms/{id} without authentication -> 401 Unauthorized
        String updateJson = """
                {
                    "roomName": "Phong Update Anon",
                    "capacity": 10,
                    "status": "AVAILABLE"
                }
                """;

        mockMvc.perform(put("/api/rooms/P-ANON")
                        .with(anonymous())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isUnauthorized());

        // DELETE /api/rooms/{id} without authentication -> 401 Unauthorized
        mockMvc.perform(delete("/api/rooms/P-ANON")
                        .with(anonymous()))
                .andExpect(status().isUnauthorized());
    }

    // ==========================================
    // US11.7 - BAO VE LICH HOP CU (KHONG CHO XOA PHONG DANG CO LICH)
    // ==========================================

    @Test
    @DisplayName("US11.7 - Khong the xoa phong dang co lich dat, tra ve HTTP 409")
    void test17_cannotDeleteRoomWithBookings() {
        Room room = new Room();
        room.setRoomId("P-DEL-LOCKED");
        room.setRoomName("Phong Co Lich");
        room.setCapacity(10);
        room.setStatus("AVAILABLE");
        roomRepository.saveAndFlush(room);

        Meeting meeting = new Meeting();
        meeting.setMeetingId("MEET-DEL");
        meeting.setRoomId("P-DEL-LOCKED");
        meeting.setOrganizerId(adminUser.getUserId());
        meeting.setTitle("Cuoc Hop Khoa Phong");
        meeting.setStartTime(LocalDateTime.now().plusDays(1));
        meeting.setEndTime(LocalDateTime.now().plusDays(1).plusHours(1));
        meeting.setStatus("SCHEDULED");
        meetingRepository.saveAndFlush(meeting);

        RoomException ex = assertThrows(RoomException.class, () -> roomController.deleteRoom("P-DEL-LOCKED"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertTrue(ex.getMessage().contains("Không thể xóa phòng vì phòng đang có lịch đặt") || ex.getMessage().contains("Khong the xoa phong vi phong dang co lich dat"));

        assertTrue(roomRepository.existsById("P-DEL-LOCKED"));
    }

    @Test
    @DisplayName("US11.7 - Xoa phong khong co lich dat thanh cong, tra ve HTTP 204")
    void test18_deleteEmptyRoomSuccessfully() {
        Room room = new Room();
        room.setRoomId("P-DEL-EMPTY");
        room.setRoomName("Phong Trong");
        room.setCapacity(10);
        room.setStatus("AVAILABLE");
        roomRepository.saveAndFlush(room);

        ResponseEntity<Void> response = roomController.deleteRoom("P-DEL-EMPTY");
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());

        assertFalse(roomRepository.existsById("P-DEL-EMPTY"));
    }

    // ==========================================
    // CAPACITIES & LOCKING ON US02 & MEETING SERVICE
    // ==========================================

    @Test
    @DisplayName("US02 - Tu choi doi lich sang phong khong du suc chua cho so nguoi tham gia, tra ve HTTP 409")
    void test19_us02CannotRescheduleToRoomExceedingCapacity() {
        Room smallRoom = new Room();
        smallRoom.setRoomId("P-SMALL-US02");
        smallRoom.setRoomName("Phong Nho US02");
        smallRoom.setCapacity(5);
        smallRoom.setStatus("AVAILABLE");
        roomRepository.saveAndFlush(smallRoom);

        Meeting meeting = new Meeting();
        meeting.setMeetingId("MEET-US02-CAP");
        meeting.setOrganizerId(attendeeUser.getUserId());
        meeting.setTitle("Cuoc hop US02");
        meeting.setStartTime(LocalDateTime.now().plusDays(3).withHour(10).withMinute(0));
        meeting.setEndTime(LocalDateTime.now().plusDays(3).withHour(11).withMinute(0));
        meeting.setStatus("SCHEDULED");
        meetingRepository.saveAndFlush(meeting);

        // Them 6 participants -> tong cong 7 nguoi (1 organizer + 6 participants) > capacity 5
        for (int i = 1; i <= 6; i++) {
            MeetingParticipant p = new MeetingParticipant();
            p.setMeetingId("MEET-US02-CAP");
            p.setUserId("PART-US02-" + i);
            p.setStatus("ACCEPTED");
            participantRepository.save(p);
        }
        participantRepository.flush();

        US02MeetingRequest req = new US02MeetingRequest(
                "Cuoc hop US02 Doi Phong",
                "Mota",
                meeting.getStartTime(),
                meeting.getEndTime(),
                "P-SMALL-US02"
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                us02MeetingService.update("MEET-US02-CAP", attendeeUser.getUserId(), req));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Room capacity exceeded"));
    }

    @Test
    @DisplayName("MeetingService - Tu choi tao cuoc hop voi phong bao tri hoac suc chua bang 0, tra ve HTTP 409")
    void test20_meetingServiceRejectsUnbookableOrZeroCapacityRoom() {
        Room zeroRoom = new Room();
        zeroRoom.setRoomId("P-ZERO-CAP");
        zeroRoom.setRoomName("Phong Suc Chua 0");
        zeroRoom.setCapacity(0);
        zeroRoom.setStatus("AVAILABLE");
        roomRepository.saveAndFlush(zeroRoom);

        com.ttcs.meetingmanagement.dto.CreateMeetingRequest req = new com.ttcs.meetingmanagement.dto.CreateMeetingRequest();
        req.setOrganizerId(attendeeUser.getUserId());
        req.setRoomId("P-ZERO-CAP");
        req.setTitle("Cuoc hop suc chua 0");
        req.setStartTime(LocalDateTime.now().plusDays(1).withHour(9).withMinute(0));
        req.setEndTime(LocalDateTime.now().plusDays(1).withHour(10).withMinute(0));

        com.ttcs.meetingmanagement.exception.MeetingException ex = assertThrows(
                com.ttcs.meetingmanagement.exception.MeetingException.class,
                () -> meetingService.createMeeting(req));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    @DisplayName("MeetingParticipantService - Tu choi them nguoi tham gia lam vuot qua suc chua phong, tra ve HTTP 409")
    void test21_addParticipantRejectsWhenExceedingRoomCapacity() {
        Room room = new Room();
        room.setRoomId("P-PART-CAP");
        room.setRoomName("Phong 2 Cho");
        room.setCapacity(2);
        room.setStatus("AVAILABLE");
        roomRepository.saveAndFlush(room);

        // Cuoc hop co phong P-PART-CAP, organizer la attendeeUser (1 nguoi)
        Meeting meeting = new Meeting();
        meeting.setMeetingId("MEET-PART-CAP");
        meeting.setOrganizerId(attendeeUser.getUserId());
        meeting.setRoomId("P-PART-CAP");
        meeting.setTitle("Cuoc hop suc chua 2");
        meeting.setStartTime(LocalDateTime.now().plusDays(2).withHour(14).withMinute(0));
        meeting.setEndTime(LocalDateTime.now().plusDays(2).withHour(15).withMinute(0));
        meeting.setStatus("SCHEDULED");
        meetingRepository.saveAndFlush(meeting);

        // Them nguoi tham du 1 (tong cong: 1 organizer + 1 participant = 2 == capacity 2) -> Thanh cong
        var resp1 = meetingParticipantService.addParticipant(
                "MEET-PART-CAP",
                new com.ttcs.meetingmanagement.dto.AddMeetingParticipantRequest(adminUser.getUserId())
        );
        assertNotNull(resp1);
        assertEquals("PENDING", resp1.status());

        // Tao them user 3
        User user3 = new User();
        user3.setUserId("USER-PART-3");
        user3.setEmail("user3-part@example.com");
        user3.setFullName("User 3 Part");
        user3.setRole(attendeeRole);
        user3.setStatus("ACTIVE");
        userRepository.saveAndFlush(user3);

        // Them nguoi tham du 2 (tong cong se la 3 > capacity 2) -> Phai bi chan HTTP 409
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                meetingParticipantService.addParticipant(
                        "MEET-PART-CAP",
                        new com.ttcs.meetingmanagement.dto.AddMeetingParticipantRequest("USER-PART-3")
                ));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Room capacity exceeded"));
    }
}
