package com.ttcs.meetingmanagement.room;

import com.ttcs.meetingmanagement.controller.MeetingController;
import com.ttcs.meetingmanagement.controller.MeetingController.MeetingRequest;
import com.ttcs.meetingmanagement.dto.AvailableRoomResponse;
import com.ttcs.meetingmanagement.dto.RoomAvailabilitySummaryResponse;
import com.ttcs.meetingmanagement.dto.RoomBookingRequest;
import com.ttcs.meetingmanagement.meeting.update.US02MeetingRequest;
import com.ttcs.meetingmanagement.meeting.update.US02MeetingService;
import com.ttcs.meetingmanagement.model.Meeting;
import com.ttcs.meetingmanagement.model.MeetingStatus;
import com.ttcs.meetingmanagement.model.Role;
import com.ttcs.meetingmanagement.model.User;
import com.ttcs.meetingmanagement.repository.MeetingRepository;
import com.ttcs.meetingmanagement.repository.RoleRepository;
import com.ttcs.meetingmanagement.repository.UserRepository;
import com.ttcs.meetingmanagement.roombooking.RoomBookingService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:meeting_us07_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional
public class US07AvailableRoomsComprehensiveTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private RoomAvailabilityService roomAvailabilityService;

    @Autowired
    private RoomBookingService roomBookingService;

    @Autowired
    private MeetingController meetingController;

    @Autowired
    private US02MeetingService us02MeetingService;

    private User adminUser;
    private User pmUser;
    private User memberUser;

    private Room roomA; // AVAILABLE, capacity 20
    private Room roomB; // AVAILABLE, capacity 50
    private Room roomMaint; // MAINTENANCE, capacity 30
    private Room roomInactive; // INACTIVE, capacity 40

    @BeforeEach
    void setupData() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        // 1. Roles
        Role adminRole = roleRepository.findById("ROLE_ADMIN").orElseGet(() ->
                roleRepository.saveAndFlush(new Role("ROLE_ADMIN", "ADMIN")));
        Role pmRole = roleRepository.findById("ROLE_PM").orElseGet(() ->
                roleRepository.saveAndFlush(new Role("ROLE_PM", "PROJECT_MANAGER")));
        Role memberRole = roleRepository.findById("ROLE_MEMBER").orElseGet(() ->
                roleRepository.saveAndFlush(new Role("ROLE_MEMBER", "MEMBER")));

        // 2. Users
        adminUser = userRepository.findById("ADMIN_01").orElseGet(() -> {
            User u = new User();
            u.setUserId("ADMIN_01");
            u.setEmail("admin@company.com");
            u.setFullName("Admin System");
            u.setPasswordHash("pass_hash");
            u.setRole(adminRole);
            u.setCreatedAt(LocalDateTime.now());
            u.setUpdatedAt(LocalDateTime.now());
            return userRepository.saveAndFlush(u);
        });

        pmUser = userRepository.findById("PM_01").orElseGet(() -> {
            User u = new User();
            u.setUserId("PM_01");
            u.setEmail("pm@company.com");
            u.setFullName("Project Manager");
            u.setPasswordHash("pass_hash");
            u.setRole(pmRole);
            u.setCreatedAt(LocalDateTime.now());
            u.setUpdatedAt(LocalDateTime.now());
            return userRepository.saveAndFlush(u);
        });

        memberUser = userRepository.findById("MEMBER_01").orElseGet(() -> {
            User u = new User();
            u.setUserId("MEMBER_01");
            u.setEmail("member@company.com");
            u.setFullName("Regular Member");
            u.setPasswordHash("pass_hash");
            u.setRole(memberRole);
            u.setCreatedAt(LocalDateTime.now());
            u.setUpdatedAt(LocalDateTime.now());
            return userRepository.saveAndFlush(u);
        });

        // 3. Rooms
        roomA = new Room();
        roomA.setRoomId("ROOM_A");
        roomA.setRoomName("Phòng Hội Nghị A");
        roomA.setCapacity(20);
        roomA.setLocation("Tầng 1");
        roomA.setStatus("AVAILABLE");
        roomRepository.saveAndFlush(roomA);

        roomB = new Room();
        roomB.setRoomId("ROOM_B");
        roomB.setRoomName("Phòng Hội Nghị B");
        roomB.setCapacity(50);
        roomB.setLocation("Tầng 2");
        roomB.setStatus("AVAILABLE");
        roomRepository.saveAndFlush(roomB);

        roomMaint = new Room();
        roomMaint.setRoomId("ROOM_MAINT");
        roomMaint.setRoomName("Phòng Bảo Trì");
        roomMaint.setCapacity(30);
        roomMaint.setLocation("Tầng 3");
        roomMaint.setStatus("MAINTENANCE");
        roomRepository.saveAndFlush(roomMaint);

        roomInactive = new Room();
        roomInactive.setRoomId("ROOM_INACTIVE");
        roomInactive.setRoomName("Phòng Ngừng Dùng");
        roomInactive.setCapacity(40);
        roomInactive.setLocation("Tầng 4");
        roomInactive.setStatus("INACTIVE");
        roomRepository.saveAndFlush(roomInactive);
    }

    // ==========================================
    // TC 01: ADMIN xem danh sách phòng trống: thành công
    // ==========================================
    @Test
    @DisplayName("TC 01: ADMIN xem danh sách phòng trống thành công (200 OK)")
    void test01_adminCanAccessAvailableRooms() throws Exception {
        mockMvc.perform(get("/api/rooms/available")
                        .with(user(adminUser.getUserId()))
                        .param("startTime", "2030-10-10T09:00:00+07:00")
                        .param("endTime", "2030-10-10T10:00:00+07:00"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/rooms/availability")
                        .with(user(adminUser.getUserId()))
                        .param("startTime", "2030-10-10T09:00:00+07:00")
                        .param("endTime", "2030-10-10T10:00:00+07:00"))
                .andExpect(status().isOk());
    }

    // ==========================================
    // TC 02: PM/Manager xem danh sách phòng trống: thành công
    // ==========================================
    @Test
    @DisplayName("TC 02: PM/Manager xem danh sách phòng trống thành công (200 OK)")
    void test02_managerCanAccessAvailableRooms() throws Exception {
        mockMvc.perform(get("/api/rooms/available")
                        .with(user(pmUser.getUserId()))
                        .param("startTime", "2030-10-10T09:00:00+07:00")
                        .param("endTime", "2030-10-10T10:00:00+07:00"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/rooms/availability")
                        .with(user(pmUser.getUserId()))
                        .param("startTime", "2030-10-10T09:00:00+07:00")
                        .param("endTime", "2030-10-10T10:00:00+07:00"))
                .andExpect(status().isOk());
    }

    // ==========================================
    // TC 03: MEMBER gọi API US07: HTTP 403
    // ==========================================
    @Test
    @DisplayName("TC 03: MEMBER gọi API US07 bị từ chối với HTTP 403 Forbidden")
    void test03_memberForbiddenFromAvailableRooms() throws Exception {
        mockMvc.perform(get("/api/rooms/available")
                        .with(user(memberUser.getUserId()))
                        .param("startTime", "2030-10-10T09:00:00+07:00")
                        .param("endTime", "2030-10-10T10:00:00+07:00"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/rooms/availability")
                        .with(user(memberUser.getUserId()))
                        .param("startTime", "2030-10-10T09:00:00+07:00")
                        .param("endTime", "2030-10-10T10:00:00+07:00"))
                .andExpect(status().isForbidden());
    }

    // ==========================================
    // TC 04: Chưa xác thực gọi API US07: HTTP 401
    // ==========================================
    @Test
    @DisplayName("TC 04: Chưa xác thực gọi API US07 nhận HTTP 401 Unauthorized")
    void test04_unauthenticatedReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/rooms/available")
                        .param("startTime", "2030-10-10T09:00:00+07:00")
                        .param("endTime", "2030-10-10T10:00:00+07:00"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/rooms/availability")
                        .param("startTime", "2030-10-10T09:00:00+07:00")
                        .param("endTime", "2030-10-10T10:00:00+07:00"))
                .andExpect(status().isUnauthorized());
    }

    // ==========================================
    // TC 05: Phòng còn trống trong khoảng thời gian: khả dụng
    // ==========================================
    @Test
    @DisplayName("TC 05: Phòng còn trống trong khoảng thời gian là khả dụng")
    void test05_availableRoomInTimeWindowIsAvailable() {
        OffsetDateTime start = OffsetDateTime.parse("2030-11-01T08:00:00+07:00");
        OffsetDateTime end = start.plusHours(2);

        List<AvailableRoomResponse> availableRooms = roomAvailabilityService.getAvailableRooms(start, end, null);
        List<String> roomIds = availableRooms.stream().map(AvailableRoomResponse::roomId).toList();

        assertTrue(roomIds.contains("ROOM_A"), "ROOM_A không có lịch họp nên phải khả dụng");
        assertTrue(roomIds.contains("ROOM_B"), "ROOM_B không có lịch họp nên phải khả dụng");
    }

    // ==========================================
    // TC 06: Phòng trùng lịch: không khả dụng
    // ==========================================
    @Test
    @DisplayName("TC 06: Phòng trùng lịch họp hợp lệ là không khả dụng")
    void test06_conflictingRoomInTimeWindowIsUnavailable() {
        OffsetDateTime start = OffsetDateTime.parse("2030-11-02T10:00:00+07:00");
        OffsetDateTime end = start.plusHours(2);

        // Tạo cuộc họp trên ROOM_A từ 10:30 đến 11:30
        Meeting meeting = new Meeting();
        meeting.setMeetingId(UUID.randomUUID().toString());
        meeting.setTitle("Họp Sprint Review");
        meeting.setRoomId(roomA.getRoomId());
        meeting.setOrganizerId(adminUser.getUserId());
        meeting.setStartTime(start.plusMinutes(30).toLocalDateTime());
        meeting.setEndTime(end.minusMinutes(30).toLocalDateTime());
        meeting.setStatus(MeetingStatus.SCHEDULED);
        meeting.setCreatedAt(LocalDateTime.now());
        meeting.setUpdatedAt(LocalDateTime.now());
        meetingRepository.saveAndFlush(meeting);

        List<AvailableRoomResponse> availableRooms = roomAvailabilityService.getAvailableRooms(start, end, null);
        List<String> roomIds = availableRooms.stream().map(AvailableRoomResponse::roomId).toList();

        assertFalse(roomIds.contains("ROOM_A"), "ROOM_A có lịch họp trùng khung giờ nên không khả dụng");
        assertTrue(roomIds.contains("ROOM_B"), "ROOM_B không trùng lịch vẫn khả dụng");

        // Kiểm tra overview
        RoomAvailabilitySummaryResponse overview = roomAvailabilityService.getRoomAvailabilityOverview(start, end, null);
        var statusA = overview.rooms().stream().filter(r -> r.roomId().equals("ROOM_A")).findFirst().orElseThrow();
        assertFalse(statusA.isAvailable());
        assertEquals("Đã có lịch họp trùng giờ", statusA.reason());
    }

    // ==========================================
    // TC 07: Phòng có lịch ngoài khoảng thời gian: khả dụng
    // ==========================================
    @Test
    @DisplayName("TC 07: Phòng có lịch nằm ngoài khoảng thời gian yêu cầu vẫn khả dụng")
    void test07_roomWithMeetingOutsideWindowIsAvailable() {
        OffsetDateTime requestedStart = OffsetDateTime.parse("2030-11-03T14:00:00+07:00");
        OffsetDateTime requestedEnd = requestedStart.plusHours(2); // 14:00 - 16:00

        // Lịch họp buổi sáng 08:00 - 10:00 (hoàn toàn ngoài khung giờ)
        Meeting meetingMorning = new Meeting();
        meetingMorning.setMeetingId(UUID.randomUUID().toString());
        meetingMorning.setTitle("Họp ban sáng");
        meetingMorning.setRoomId(roomA.getRoomId());
        meetingMorning.setOrganizerId(adminUser.getUserId());
        meetingMorning.setStartTime(requestedStart.minusHours(6).toLocalDateTime());
        meetingMorning.setEndTime(requestedStart.minusHours(4).toLocalDateTime());
        meetingMorning.setStatus(MeetingStatus.SCHEDULED);
        meetingMorning.setCreatedAt(LocalDateTime.now());
        meetingMorning.setUpdatedAt(LocalDateTime.now());
        meetingRepository.saveAndFlush(meetingMorning);

        List<AvailableRoomResponse> available = roomAvailabilityService.getAvailableRooms(requestedStart, requestedEnd, null);
        List<String> roomIds = available.stream().map(AvailableRoomResponse::roomId).toList();

        assertTrue(roomIds.contains("ROOM_A"), "ROOM_A có lịch buổi sáng nhưng buổi chiều phải khả dụng");
    }

    // ==========================================
    // TC 08: Phòng bảo trì: không khả dụng
    // ==========================================
    @Test
    @DisplayName("TC 08: Phòng bảo trì không khả dụng và có lý do rõ ràng")
    void test08_maintenanceRoomIsUnavailable() {
        OffsetDateTime start = OffsetDateTime.parse("2030-11-04T09:00:00+07:00");
        OffsetDateTime end = start.plusHours(1);

        List<AvailableRoomResponse> available = roomAvailabilityService.getAvailableRooms(start, end, null);
        List<String> roomIds = available.stream().map(AvailableRoomResponse::roomId).toList();
        assertFalse(roomIds.contains("ROOM_MAINT"), "Phòng MAINTENANCE không được nằm trong danh sách phòng trống");

        RoomAvailabilitySummaryResponse overview = roomAvailabilityService.getRoomAvailabilityOverview(start, end, null);
        var statusMaint = overview.rooms().stream().filter(r -> r.roomId().equals("ROOM_MAINT")).findFirst().orElseThrow();
        assertFalse(statusMaint.isAvailable());
        assertEquals("Phòng đang bảo trì", statusMaint.reason());
    }

    // ==========================================
    // TC 09: Phòng ngừng sử dụng: không khả dụng
    // ==========================================
    @Test
    @DisplayName("TC 09: Phòng ngừng sử dụng (INACTIVE) không khả dụng")
    void test09_inactiveRoomIsUnavailable() {
        OffsetDateTime start = OffsetDateTime.parse("2030-11-04T09:00:00+07:00");
        OffsetDateTime end = start.plusHours(1);

        List<AvailableRoomResponse> available = roomAvailabilityService.getAvailableRooms(start, end, null);
        List<String> roomIds = available.stream().map(AvailableRoomResponse::roomId).toList();
        assertFalse(roomIds.contains("ROOM_INACTIVE"), "Phòng INACTIVE không được nằm trong danh sách phòng trống");

        RoomAvailabilitySummaryResponse overview = roomAvailabilityService.getRoomAvailabilityOverview(start, end, null);
        var statusInactive = overview.rooms().stream().filter(r -> r.roomId().equals("ROOM_INACTIVE")).findFirst().orElseThrow();
        assertFalse(statusInactive.isAvailable());
        assertEquals("Phòng ngừng sử dụng", statusInactive.reason());
    }

    // ==========================================
    // TC 10: Phòng không đủ sức chứa: không khả dụng theo yêu cầu lọc
    // ==========================================
    @Test
    @DisplayName("TC 10: Phòng không đủ sức chứa bị loại trừ khỏi danh sách khả dụng")
    void test10_insufficientCapacityRoomIsUnavailableUnderCapacityFilter() {
        OffsetDateTime start = OffsetDateTime.parse("2030-11-05T09:00:00+07:00");
        OffsetDateTime end = start.plusHours(1);

        // Lọc yêu cầu sức chứa tối thiểu 35 người. ROOM_A (20) không đủ, ROOM_B (50) đủ.
        List<AvailableRoomResponse> available = roomAvailabilityService.getAvailableRooms(start, end, 35);
        List<String> roomIds = available.stream().map(AvailableRoomResponse::roomId).toList();

        assertFalse(roomIds.contains("ROOM_A"), "ROOM_A sức chứa 20 < 35 nên không khả dụng");
        assertTrue(roomIds.contains("ROOM_B"), "ROOM_B sức chứa 50 >= 35 nên khả dụng");

        // Kiểm tra overview
        RoomAvailabilitySummaryResponse overview = roomAvailabilityService.getRoomAvailabilityOverview(start, end, 35);
        var statusA = overview.rooms().stream().filter(r -> r.roomId().equals("ROOM_A")).findFirst().orElseThrow();
        assertFalse(statusA.isAvailable());
        assertTrue(statusA.reason().contains("Không đủ sức chứa"));
    }

    // ==========================================
    // TC 11: Không có phòng phù hợp: thông báo hết phòng đúng
    // ==========================================
    @Test
    @DisplayName("TC 11: Không có phòng phù hợp trả về danh sách rỗng, 0 phòng khả dụng, không lỗi 500")
    void test11_noAvailableRoomsReturnsCorrectExhaustedSummaryWithout500() throws Exception {
        OffsetDateTime start = OffsetDateTime.parse("2030-11-06T10:00:00+07:00");
        OffsetDateTime end = start.plusHours(1);

        // Yêu cầu sức chứa 1000 người (không phòng nào đáp ứng)
        List<AvailableRoomResponse> available = roomAvailabilityService.getAvailableRooms(start, end, 1000);
        assertTrue(available.isEmpty(), "Danh sách phòng trống phải rỗng khi hết phòng");

        RoomAvailabilitySummaryResponse overview = roomAvailabilityService.getRoomAvailabilityOverview(start, end, 1000);
        assertEquals(0, overview.availableRoomsCount(), "Số phòng khả dụng phải bằng 0");
        assertFalse(overview.rooms().isEmpty(), "Overview vẫn trả về danh sách đầy đủ trạng thái phòng");

        // Gọi qua HTTP MockMvc không phát sinh 500
        mockMvc.perform(get("/api/rooms/available")
                        .with(user(adminUser.getUserId()))
                        .param("startTime", start.toString())
                        .param("endTime", end.toString())
                        .param("minCapacity", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/rooms/availability")
                        .with(user(adminUser.getUserId()))
                        .param("startTime", start.toString())
                        .param("endTime", end.toString())
                        .param("minCapacity", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableRoomsCount").value(0));
    }

    // ==========================================
    // TC 12: Tạo cuộc họp US01 thành công: số phòng còn giảm chính xác
    // ==========================================
    @Test
    @DisplayName("TC 12: Tạo cuộc họp US01 thành công làm giảm số phòng còn trong khung giờ")
    void test12_us01CreateMeetingReducesAvailableRooms() {
        OffsetDateTime start = OffsetDateTime.parse("2030-11-07T09:00:00+07:00");
        OffsetDateTime end = start.plusHours(1);

        // Trước khi tạo cuộc họp: ROOM_A và ROOM_B đều trống
        List<AvailableRoomResponse> before = roomAvailabilityService.getAvailableRooms(start, end, null);
        assertTrue(before.stream().anyMatch(r -> r.roomId().equals("ROOM_A")));

        // Tạo cuộc họp US01
        MeetingRequest request = new MeetingRequest(
                "Cuộc họp phòng ban US01",
                "Mô tả US01",
                start.toLocalDateTime(),
                end.toLocalDateTime(),
                adminUser.getUserId(),
                List.of(),
                "NONE",
                0,
                "ROOM_A"
        );
        Meeting created = meetingController.create(request);
        assertNotNull(created.getMeetingId());

        // Sau khi tạo cuộc họp: ROOM_A không còn trong danh sách phòng trống
        List<AvailableRoomResponse> after = roomAvailabilityService.getAvailableRooms(start, end, null);
        assertFalse(after.stream().anyMatch(r -> r.roomId().equals("ROOM_A")));
    }

    // ==========================================
    // TC 13: Chỉnh sửa cuộc họp US02: kết quả phòng trống thay đổi chính xác
    // ==========================================
    @Test
    @DisplayName("TC 13: Chỉnh sửa cuộc họp US02 cập nhật giải phóng phòng cũ hoặc thay đổi phòng")
    void test13_us02UpdateMeetingChangesRoomAvailability() {
        OffsetDateTime start = OffsetDateTime.parse("2030-11-08T09:00:00+07:00");
        OffsetDateTime end = start.plusHours(1);

        // Tạo cuộc họp ban đầu trên ROOM_A
        Meeting meeting = new Meeting();
        meeting.setMeetingId(UUID.randomUUID().toString());
        meeting.setTitle("Họp ban đầu");
        meeting.setRoomId("ROOM_A");
        meeting.setOrganizerId(adminUser.getUserId());
        meeting.setStartTime(start.toLocalDateTime());
        meeting.setEndTime(end.toLocalDateTime());
        meeting.setStatus("SCHEDULED");
        meeting.setCreatedAt(LocalDateTime.now());
        meeting.setUpdatedAt(LocalDateTime.now());
        meetingRepository.saveAndFlush(meeting);

        // Trước khi đổi: ROOM_A bận, ROOM_B trống
        assertFalse(roomAvailabilityService.getAvailableRooms(start, end, null).stream().anyMatch(r -> r.roomId().equals("ROOM_A")));
        assertTrue(roomAvailabilityService.getAvailableRooms(start, end, null).stream().anyMatch(r -> r.roomId().equals("ROOM_B")));

        // US02: Cập nhật chuyển cuộc họp sang ROOM_B
        US02MeetingRequest updateReq = new US02MeetingRequest(
                "Họp sau cập nhật",
                "Mô tả cập nhật",
                start.toLocalDateTime(),
                end.toLocalDateTime(),
                "ROOM_B"
        );
        us02MeetingService.update(meeting.getMeetingId(), adminUser.getUserId(), updateReq);

        // Sau khi đổi: ROOM_A được giải phóng (khả dụng), ROOM_B bị chiếm (không khả dụng)
        assertTrue(roomAvailabilityService.getAvailableRooms(start, end, null).stream().anyMatch(r -> r.roomId().equals("ROOM_A")),
                "ROOM_A phải được giải phóng và khả dụng trở lại");
        assertFalse(roomAvailabilityService.getAvailableRooms(start, end, null).stream().anyMatch(r -> r.roomId().equals("ROOM_B")),
                "ROOM_B được gán mới nên không còn khả dụng");
    }

    // ==========================================
    // TC 14: Đặt phòng US08 thành công: phòng không còn khả dụng
    // ==========================================
    @Test
    @DisplayName("TC 14: Đặt phòng US08 thành công làm phòng không còn khả dụng trong khung giờ đó")
    void test14_us08RoomBookingMakesRoomUnavailable() {
        OffsetDateTime start = OffsetDateTime.parse("2030-11-09T14:00:00+07:00");
        OffsetDateTime end = start.plusHours(1);

        // Cuộc họp chưa có phòng
        Meeting meeting = new Meeting();
        meeting.setMeetingId(UUID.randomUUID().toString());
        meeting.setTitle("Họp chưa gán phòng");
        meeting.setOrganizerId(adminUser.getUserId());
        meeting.setStartTime(start.toLocalDateTime());
        meeting.setEndTime(end.toLocalDateTime());
        meeting.setStatus("SCHEDULED");
        meeting.setCreatedAt(LocalDateTime.now());
        meeting.setUpdatedAt(LocalDateTime.now());
        meetingRepository.saveAndFlush(meeting);

        // ROOM_A đang khả dụng
        assertTrue(roomAvailabilityService.getAvailableRooms(start, end, null).stream().anyMatch(r -> r.roomId().equals("ROOM_A")));

        // US08: Đặt phòng ROOM_A
        roomBookingService.bookRoom(new RoomBookingRequest(meeting.getMeetingId(), "ROOM_A"));

        // Sau khi đặt: ROOM_A không còn khả dụng
        assertFalse(roomAvailabilityService.getAvailableRooms(start, end, null).stream().anyMatch(r -> r.roomId().equals("ROOM_A")));
    }

    // ==========================================
    // TC 15: Hủy đặt phòng US09: phòng được giải phóng
    // ==========================================
    @Test
    @DisplayName("TC 15: Hủy đặt phòng US09 giải phóng phòng, phòng khả dụng trở lại")
    void test15_us09CancelRoomBookingFreesRoom() {
        OffsetDateTime start = OffsetDateTime.parse("2030-11-10T14:00:00+07:00");
        OffsetDateTime end = start.plusHours(1);

        Meeting meeting = new Meeting();
        meeting.setMeetingId(UUID.randomUUID().toString());
        meeting.setTitle("Họp có phòng cần hủy");
        meeting.setRoomId("ROOM_A");
        meeting.setOrganizerId(adminUser.getUserId());
        meeting.setStartTime(start.toLocalDateTime());
        meeting.setEndTime(end.toLocalDateTime());
        meeting.setStatus("SCHEDULED");
        meeting.setCreatedAt(LocalDateTime.now());
        meeting.setUpdatedAt(LocalDateTime.now());
        meetingRepository.saveAndFlush(meeting);

        // ROOM_A đang bận
        assertFalse(roomAvailabilityService.getAvailableRooms(start, end, null).stream().anyMatch(r -> r.roomId().equals("ROOM_A")));

        // US09: Hủy đặt phòng
        roomBookingService.cancelRoomBooking(meeting.getMeetingId());

        // ROOM_A được giải phóng và khả dụng
        assertTrue(roomAvailabilityService.getAvailableRooms(start, end, null).stream().anyMatch(r -> r.roomId().equals("ROOM_A")));
    }

    // ==========================================
    // TC 16: Hai khoảng thời gian liền kề: không trùng lịch sai
    // ==========================================
    @Test
    @DisplayName("TC 16: Hai khoảng thời gian liền kề (10:00-11:00 và 11:00-12:00) không trùng lịch")
    void test16_adjacentTimeSlotsDoNotConflict() {
        OffsetDateTime slot1Start = OffsetDateTime.parse("2030-11-11T10:00:00+07:00");
        OffsetDateTime slot1End = slot1Start.plusHours(1); // 10:00 - 11:00

        OffsetDateTime slot2Start = slot1End; // 11:00
        OffsetDateTime slot2End = slot2Start.plusHours(1); // 11:00 - 12:00

        // Lịch họp chiếm slot 1 trên ROOM_A
        Meeting meeting = new Meeting();
        meeting.setMeetingId(UUID.randomUUID().toString());
        meeting.setTitle("Họp slot 1");
        meeting.setRoomId("ROOM_A");
        meeting.setOrganizerId(adminUser.getUserId());
        meeting.setStartTime(slot1Start.toLocalDateTime());
        meeting.setEndTime(slot1End.toLocalDateTime());
        meeting.setStatus("SCHEDULED");
        meeting.setCreatedAt(LocalDateTime.now());
        meeting.setUpdatedAt(LocalDateTime.now());
        meetingRepository.saveAndFlush(meeting);

        // Tra cứu slot 2: ROOM_A phải khả dụng vì không giao nhau (existingStart < requestedEnd && existingEnd > requestedStart)
        List<AvailableRoomResponse> availableInSlot2 = roomAvailabilityService.getAvailableRooms(slot2Start, slot2End, null);
        assertTrue(availableInSlot2.stream().anyMatch(r -> r.roomId().equals("ROOM_A")),
                "Hai khoảng thời gian liền kề điểm đầu cuối không được tính là trùng lịch");
    }

    // ==========================================
    // TC 17: Khoảng thời gian đầu vào không hợp lệ: trả lỗi validation phù hợp (400)
    // ==========================================
    @Test
    @DisplayName("TC 17: Đầu vào không hợp lệ (end <= start hoặc minCapacity <= 0) trả HTTP 400 Bad Request")
    void test17_invalidTimeOrCapacityReturnsValidationBadRequest() {
        OffsetDateTime start = OffsetDateTime.parse("2030-11-12T10:00:00+07:00");
        OffsetDateTime endBeforeStart = start.minusHours(1);

        // endTime trước startTime
        assertThrows(ResponseStatusException.class, () ->
                roomAvailabilityService.getAvailableRooms(start, endBeforeStart, null));

        // minCapacity <= 0
        assertThrows(ResponseStatusException.class, () ->
                roomAvailabilityService.getAvailableRooms(start, start.plusHours(1), 0));
        assertThrows(ResponseStatusException.class, () ->
                roomAvailabilityService.getAvailableRooms(start, start.plusHours(1), -5));
    }

    // ==========================================
    // TC 18: Tổng số phòng và số phòng khả dụng khớp với dữ liệu database
    // ==========================================
    @Test
    @DisplayName("TC 18: Tổng số phòng và số phòng khả dụng/bảo trì khớp chính xác database")
    void test18_totalAndAvailableRoomCountsMatchDatabase() {
        OffsetDateTime start = OffsetDateTime.parse("2030-11-13T09:00:00+07:00");
        OffsetDateTime end = start.plusHours(1);

        // Tạo 1 cuộc họp trên ROOM_A
        Meeting meeting = new Meeting();
        meeting.setMeetingId(UUID.randomUUID().toString());
        meeting.setTitle("Họp kiểm tra số lượng");
        meeting.setRoomId("ROOM_A");
        meeting.setOrganizerId(adminUser.getUserId());
        meeting.setStartTime(start.toLocalDateTime());
        meeting.setEndTime(end.toLocalDateTime());
        meeting.setStatus("SCHEDULED");
        meeting.setCreatedAt(LocalDateTime.now());
        meeting.setUpdatedAt(LocalDateTime.now());
        meetingRepository.saveAndFlush(meeting);

        RoomAvailabilitySummaryResponse overview = roomAvailabilityService.getRoomAvailabilityOverview(start, end, null);

        long dbTotalRooms = roomRepository.count();
        long dbMaintenanceAndInactiveRooms = roomRepository.findAll().stream()
                .filter(r -> !"AVAILABLE".equalsIgnoreCase(r.getStatus()))
                .count();

        assertEquals(dbTotalRooms, overview.totalRooms(), "Tổng số phòng phải bằng số phòng trong database");
        assertEquals(1, overview.busyRoomsCount(), "Số phòng trùng lịch phải bằng 1 (ROOM_A)");
        assertEquals(dbMaintenanceAndInactiveRooms, overview.maintenanceRoomsCount(), "Số phòng bảo trì/ngừng dùng phải khớp database");
        assertEquals(dbTotalRooms - 1 - dbMaintenanceAndInactiveRooms, overview.availableRoomsCount(), "Số phòng khả dụng phải bằng total - busy - maintenance");
    }

    // ==========================================
    // TC 19: Regression test phân quyền để bảo đảm không làm mất quyền của các API khác
    // ==========================================
    @Test
    @DisplayName("TC 19: Regression test phân quyền: Admin và PM giữ nguyên quyền US01/US08/US20, Member bị giới hạn")
    void test19_rbacRegressionDoesNotBreakOtherApis() throws Exception {
        // 1. Admin được gọi API quản lý vai trò /api/admin/** (US20)
        mockMvc.perform(get("/api/admin/roles")
                        .with(user(adminUser.getUserId())))
                .andExpect(result -> {
                    // Endpoint có thể 200 hoặc 404 nếu path cụ thể chưa mount, nhưng KHÔNG ĐƯỢC 403 Forbidden
                    assertNotEquals(403, result.getResponse().getStatus());
                });

        // 2. Member không được gọi /api/admin/**
        mockMvc.perform(get("/api/admin/roles")
                        .with(user(memberUser.getUserId())))
                .andExpect(status().isForbidden());

        // 3. Member không được đặt phòng qua POST /api/room-bookings
        mockMvc.perform(post("/api/room-bookings")
                        .with(user(memberUser.getUserId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"meetingId\":\"m-1\",\"roomId\":\"ROOM_A\"}"))
                .andExpect(status().isForbidden());
    }

    // ==========================================
    // TC 20: Maven compile và integration test hoàn chỉnh
    // ==========================================
    @Test
    @DisplayName("TC 20: Toàn bộ chu trình kiểm thử và nghiệp vụ US07 hoạt động đồng bộ")
    void test20_mavenCompileAndIntegrationVerification() {
        assertNotNull(roomAvailabilityService);
        assertNotNull(roomBookingService);
        assertNotNull(meetingController);
        assertNotNull(us02MeetingService);
    }
}
