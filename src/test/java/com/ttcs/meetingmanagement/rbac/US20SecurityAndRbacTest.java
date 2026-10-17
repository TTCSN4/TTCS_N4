package com.ttcs.meetingmanagement.rbac;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.ttcs.meetingmanagement.auth.AuthService;
import com.ttcs.meetingmanagement.auth.JwtTokenService;
import com.ttcs.meetingmanagement.auth.LoginRequest;
import com.ttcs.meetingmanagement.auth.LoginResponse;
import com.ttcs.meetingmanagement.controller.MeetingController;
import com.ttcs.meetingmanagement.controller.MeetingController.MeetingRequest;
import com.ttcs.meetingmanagement.controller.RoleManagementController;
import com.ttcs.meetingmanagement.dto.RoomBookingRequest;
import com.ttcs.meetingmanagement.dto.RoomBookingResponse;
import com.ttcs.meetingmanagement.meeting.update.US02MeetingRequest;
import com.ttcs.meetingmanagement.meeting.update.US02MeetingService;
import com.ttcs.meetingmanagement.model.Meeting;
import com.ttcs.meetingmanagement.model.Role;
import com.ttcs.meetingmanagement.model.User;
import com.ttcs.meetingmanagement.repository.MeetingRepository;
import com.ttcs.meetingmanagement.repository.RoleRepository;
import com.ttcs.meetingmanagement.repository.UserRepository;
import com.ttcs.meetingmanagement.room.Room;
import com.ttcs.meetingmanagement.room.RoomRepository;
import com.ttcs.meetingmanagement.roombooking.RoomBookingService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class US20SecurityAndRbacTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private AuthService authService;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private RoleManagementService roleManagementService;

    @Autowired
    private RoleManagementController roleManagementController;

    @Autowired
    private RbacAccess rbacAccess;

    @Autowired
    private MeetingController meetingController;

    @Autowired
    private US02MeetingService us02MeetingService;

    @Autowired
    private RoomBookingService roomBookingService;

    private Role adminRole;
    private Role pmRole;
    private Role memberRole;

    private User adminUser1;
    private User adminUser2;
    private User pmUser;
    private User otherPmUser;
    private User memberUser;
    private User lockedUser;

    private Authentication adminAuth;
    private Authentication pmAuth;
    private Authentication otherPmAuth;
    private Authentication memberAuth;
    private Authentication lockedAuth;

    private Room testRoom;

    @BeforeEach
    void setUp() {
        // 1. Khoi tao cac vai tro he thong
        adminRole = roleRepository.findById("ROLE_ADMIN_TEST")
                .orElseGet(() -> {
                    Role r = new Role();
                    r.setRoleId("ROLE_ADMIN_TEST");
                    r.setRoleName("Admin");
                    return roleRepository.saveAndFlush(r);
                });

        pmRole = roleRepository.findById("ROLE_PM_TEST")
                .orElseGet(() -> {
                    Role r = new Role();
                    r.setRoleId("ROLE_PM_TEST");
                    r.setRoleName("Người đặt lịch");
                    return roleRepository.saveAndFlush(r);
                });

        memberRole = roleRepository.findById("ROLE_MEMBER_TEST")
                .orElseGet(() -> {
                    Role r = new Role();
                    r.setRoleId("ROLE_MEMBER_TEST");
                    r.setRoleName("Người tham dự");
                    return roleRepository.saveAndFlush(r);
                });

        // 2. Khoi tao nguoi dung
        adminUser1 = createUser("U20-ADMIN-1", "admin1@test.com", "Admin One", "Admin@123", adminRole, "ACTIVE");
        adminUser2 = createUser("U20-ADMIN-2", "admin2@test.com", "Admin Two", "Admin@123", adminRole, "ACTIVE");
        pmUser = createUser("U20-PM-1", "pm1@test.com", "PM One", "Pm@123", pmRole, "ACTIVE");
        otherPmUser = createUser("U20-PM-2", "pm2@test.com", "PM Two", "Pm@123", pmRole, "ACTIVE");
        memberUser = createUser("U20-MEMBER-1", "member1@test.com", "Member One", "Member@123", memberRole, "ACTIVE");
        lockedUser = createUser("U20-LOCKED-1", "locked@test.com", "Locked User", "Locked@123", pmRole, "LOCKED");

        // 3. Khoi tao Authentication objects
        adminAuth = createAuth(adminUser1, RoleCategory.ADMIN);
        pmAuth = createAuth(pmUser, RoleCategory.PM);
        otherPmAuth = createAuth(otherPmUser, RoleCategory.PM);
        memberAuth = createAuth(memberUser, RoleCategory.MEMBER);
        lockedAuth = createAuth(lockedUser, RoleCategory.PM);

        // 4. Khoi tao phong hop mau
        testRoom = roomRepository.findById("R-US20-TEST")
                .orElseGet(() -> {
                    Room r = new Room();
                    r.setRoomId("R-US20-TEST");
                    r.setRoomName("Phòng Họp US20");
                    r.setCapacity(20);
                    r.setStatus("AVAILABLE");
                    return roomRepository.saveAndFlush(r);
                });
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private User createUser(String id, String email, String name, String rawPassword, Role role, String status) {
        return userRepository.findById(id).map(existing -> {
            existing.setEmail(email);
            existing.setFullName(name);
            existing.setPasswordHash(passwordEncoder.encode(rawPassword));
            existing.setRole(role);
            existing.setStatus(status);
            return userRepository.saveAndFlush(existing);
        }).orElseGet(() -> {
            User u = new User();
            u.setUserId(id);
            u.setEmail(email);
            u.setFullName(name);
            u.setPasswordHash(passwordEncoder.encode(rawPassword));
            u.setRole(role);
            u.setStatus(status);
            return userRepository.saveAndFlush(u);
        });
    }

    private Authentication createAuth(User u, RoleCategory category) {
        AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(
                u.getUserId(),
                u.getEmail(),
                u.getFullName(),
                u.getRole() != null ? u.getRole().getRoleId() : null,
                u.getRole() != null ? u.getRole().getRoleName() : null,
                category
        );
        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + category.name()))
        );
    }

    // ==========================================
    // 1. XAC THUC NGUOI DUNG THUC TE (AUTH SUBSYSTEM)
    // ==========================================

    @Test
    @DisplayName("1. Dang nhap thanh cong voi tai khoan hop le -> Tra ve Token va User Info")
    void test01_LoginWithValidCredentials_Succeeds() {
        LoginRequest req = new LoginRequest("admin1@test.com", "Admin@123");
        LoginResponse resp = authService.login(req);

        assertNotNull(resp);
        assertNotNull(resp.token());
        assertEquals("Bearer", resp.tokenType());
        assertEquals(adminUser1.getUserId(), resp.user().userId());
        assertEquals("ADMIN", resp.user().roleCategory());
    }

    @Test
    @DisplayName("2. Dang nhap voi sai mat khau -> 401 Unauthorized")
    void test02_LoginWithWrongPassword_Throws401() {
        LoginRequest req = new LoginRequest("admin1@test.com", "WrongPassword");
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> authService.login(req));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    @DisplayName("3. Dang nhap voi tai khoan bi khoa (LOCKED) -> 403 Forbidden")
    void test03_LoginWithLockedAccount_Throws403() {
        LoginRequest req = new LoginRequest("locked@test.com", "Locked@123");
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> authService.login(req));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertTrue(ex.getReason().contains("locked") || ex.getReason().contains("inactive"));
    }

    @Test
    @DisplayName("4. Dang nhap voi tai khoan khong ton tai -> 401 Unauthorized")
    void test04_LoginWithNonExistentAccount_Throws401() {
        LoginRequest req = new LoginRequest("ghost@test.com", "Ghost@123");
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> authService.login(req));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    @DisplayName("5. Dang nhap voi tai khoan co role null -> 403 Forbidden")
    void test05_LoginWithNullRoleAccount_Throws403() {
        User noRoleUser = createUser("U20-NO-ROLE", "norole@test.com", "No Role", "NoRole@123", null, "ACTIVE");
        LoginRequest req = new LoginRequest("norole@test.com", "NoRole@123");
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> authService.login(req));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    // ==========================================
    // 2. JWT TOKEN VALIDATION & SECURITY
    // ==========================================

    @Test
    @DisplayName("6. JWT Token tao boi JwtTokenService duoc verify chinh xac")
    void test06_JwtTokenGenerationAndValidation() {
        String token = jwtTokenService.generateToken(pmUser);
        assertNotNull(token);

        var claims = jwtTokenService.parseAndValidate(token);
        assertNotNull(claims);
        assertEquals(pmUser.getUserId(), claims.getSubject());
        assertEquals("meeting-management", claims.getIssuer());
        assertTrue(claims.getAudience().contains("meeting-management-client"));
        assertTrue(claims.getExpirationTime().after(new Date()));
    }

    @Test
    @DisplayName("7. JWT gia mao chu ky bi tu choi")
    void test07_TamperedJwtToken_Rejected() {
        String validToken = jwtTokenService.generateToken(adminUser1);
        String tamperedToken = validToken.substring(0, validToken.lastIndexOf('.') + 1) + "InVaLiDSiGNaTuRe";

        assertNull(jwtTokenService.parseAndValidate(tamperedToken));
    }

    @Test
    @DisplayName("8. JWT het han bi tu choi")
    void test08_ExpiredJwtToken_Rejected() throws Exception {
        Date now = new Date();
        Date expiredTime = new Date(now.getTime() - 3600 * 1000);

        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject("U20-ADMIN-1")
                .issuer("meeting-management")
                .audience(List.of("meeting-management-client"))
                .issueTime(new Date(now.getTime() - 7200 * 1000))
                .expirationTime(expiredTime)
                .build();

        JWSSigner signer = new MACSigner(jwtTokenService.getSecretKeyBytes());
        SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claimsSet);
        signedJWT.sign(signer);
        String expiredToken = signedJWT.serialize();

        assertNull(jwtTokenService.parseAndValidate(expiredToken));
    }

    // ==========================================
    // 3. RBAC ACCESS RULES (ADMIN, PM, MEMBER)
    // ==========================================

    @Test
    @DisplayName("9. Admin co toan quyen Admin, canBook, canAttend")
    void test09_AdminPermissions() {
        assertTrue(rbacAccess.isAdmin(adminAuth));
        assertTrue(rbacAccess.canBook(adminAuth));
        assertTrue(rbacAccess.canAttend(adminAuth));
        assertFalse(rbacAccess.isPm(adminAuth));
        assertFalse(rbacAccess.isMember(adminAuth));
    }

    @Test
    @DisplayName("10. PM co quyen canBook, canAttend; KHONG co quyen Admin")
    void test10_PmPermissions() {
        assertFalse(rbacAccess.isAdmin(pmAuth));
        assertTrue(rbacAccess.isPm(pmAuth));
        assertTrue(rbacAccess.canBook(pmAuth));
        assertTrue(rbacAccess.canAttend(pmAuth));
        assertFalse(rbacAccess.isMember(pmAuth));
    }

    @Test
    @DisplayName("11. Member co quyen canAttend; KHONG co quyen canBook va Admin")
    void test11_MemberPermissions() {
        assertFalse(rbacAccess.isAdmin(memberAuth));
        assertFalse(rbacAccess.isPm(memberAuth));
        assertFalse(rbacAccess.canBook(memberAuth));
        assertTrue(rbacAccess.isMember(memberAuth));
        assertTrue(rbacAccess.canAttend(memberAuth));
    }

    @Test
    @DisplayName("12. Tai khoan bi khoa (LOCKED) bi tu choi tren toan bo cac quyen")
    void test12_LockedAccountPermissions_AllDenied() {
        assertFalse(rbacAccess.isAdmin(lockedAuth));
        assertFalse(rbacAccess.isPm(lockedAuth));
        assertFalse(rbacAccess.canBook(lockedAuth));
        assertFalse(rbacAccess.canAttend(lockedAuth));
        assertFalse(rbacAccess.isMember(lockedAuth));
    }

    @Test
    @DisplayName("13. Authentication null hoac chua xac thuc bi tu choi")
    void test13_UnauthenticatedRequests_Denied() {
        assertFalse(rbacAccess.isAdmin((Authentication) null));
        assertFalse(rbacAccess.canBook((Authentication) null));
        assertFalse(rbacAccess.canAttend((Authentication) null));
    }

    // ==========================================
    // 4. QUAN LY VAI TRO (ROLE MANAGEMENT)
    // ==========================================

    @Test
    @DisplayName("14. Admin gan role cho nguoi dung -> Database duoc cap nhat chinh xac")
    void test14_AdminAssignRole_UpdatesDatabase() {
        SecurityContextHolder.getContext().setAuthentication(adminAuth);

        var result = roleManagementService.assignRole(memberUser.getUserId(), pmRole.getRoleId());
        assertNotNull(result);
        assertEquals(pmRole.getRoleId(), result.roleId());
        assertEquals("PM", result.roleCategory());

        User reloaded = userRepository.findWithRole(memberUser.getUserId()).orElseThrow();
        assertEquals(pmRole.getRoleId(), reloaded.getRole().getRoleId());
    }

    @Test
    @DisplayName("15. Ha quyen Admin cuoi cung bi tu choi 409 Conflict")
    void test15_DemotingLastAdmin_Throws409Conflict() {
        SecurityContextHolder.getContext().setAuthentication(adminAuth);

        // Chuyen adminUser2 sang PM de chi con duy nhat adminUser1 la Admin
        adminUser2.setRole(pmRole);
        userRepository.saveAndFlush(adminUser2);

        // Co ha quyen adminUser1 xuong Member
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                roleManagementService.assignRole(adminUser1.getUserId(), memberRole.getRoleId())
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("last administrator"));

        // Kiem tra trong DB van la Admin
        User check = userRepository.findWithRole(adminUser1.getUserId()).orElseThrow();
        assertEquals(adminRole.getRoleId(), check.getRole().getRoleId());
    }

    @Test
    @DisplayName("16. Role thay doi trong DB -> Request tiep theo lap tuc ap dung quyen moi")
    void test16_RoleChangeInDbAppliesImmediatelyWithoutTokenChange() {
        // Ban dau memberAuth khong co quyen canBook
        assertFalse(rbacAccess.canBook(memberAuth));

        // Admin cap quyen PM cho memberUser trong DB
        memberUser.setRole(pmRole);
        userRepository.saveAndFlush(memberUser);

        // Kiem tra lai voi chinh memberAuth -> lap tuc co quyen canBook!
        assertTrue(rbacAccess.canBook(memberAuth));
    }

    // ==========================================
    // 5. PHAN QUYEN THAO TAC CUOC HOP & DAT PHONG (MEETING & BOOKING)
    // ==========================================

    @Test
    @DisplayName("17. PM tao cuoc hop voi organizerId cua chinh minh -> Thanh cong")
    void test17_PmCreatesMeetingWithOwnOrganizerId_Succeeds() {
        SecurityContextHolder.getContext().setAuthentication(pmAuth);

        MeetingRequest req = new MeetingRequest(
                "Cuoc hop cua PM",
                "Mo ta",
                LocalDateTime.now().plusDays(10),
                LocalDateTime.now().plusDays(10).plusHours(1),
                pmUser.getUserId(),
                List.of(),
                "NONE",
                0,
                null
        );

        Meeting meeting = meetingController.create(req);
        assertNotNull(meeting);
        assertEquals(pmUser.getUserId(), meeting.getOrganizerId());
    }

    @Test
    @DisplayName("18. PM co tinh gia mao organizerId cua nguoi khac -> 403 Forbidden")
    void test18_PmForgesOrganizerId_Throws403() {
        SecurityContextHolder.getContext().setAuthentication(pmAuth);

        MeetingRequest req = new MeetingRequest(
                "Cuoc hop gia mao",
                "Mo ta",
                LocalDateTime.now().plusDays(11),
                LocalDateTime.now().plusDays(11).plusHours(1),
                adminUser1.getUserId(), // Gia mao organizerId cua Admin!
                List.of(),
                "NONE",
                0,
                null
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                meetingController.create(req)
        );
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    @DisplayName("19. PM sua cuoc hop cua chinh minh -> Thanh cong")
    void test19_PmModifiesOwnMeeting_Succeeds() {
        Meeting meeting = new Meeting();
        meeting.setMeetingId("MEET-PM-1");
        meeting.setTitle("Cuoc hop ban dau");
        meeting.setOrganizerId(pmUser.getUserId());
        meeting.setStartTime(LocalDateTime.now().plusDays(12));
        meeting.setEndTime(LocalDateTime.now().plusDays(12).plusHours(1));
        meeting.setStatus("SCHEDULED");
        meetingRepository.saveAndFlush(meeting);

        US02MeetingRequest updateReq = new US02MeetingRequest(
                "Cuoc hop cap nhat",
                "Mo ta moi",
                meeting.getStartTime().plusHours(1),
                meeting.getEndTime().plusHours(1),
                null
        );

        Meeting updated = us02MeetingService.update(meeting.getMeetingId(), pmUser.getUserId(), updateReq);
        assertEquals("Cuoc hop cap nhat", updated.getTitle());
    }

    @Test
    @DisplayName("20. PM sua cuoc hop cua PM khac -> 403 Forbidden")
    void test20_PmModifiesOthersMeeting_Throws403() {
        Meeting meeting = new Meeting();
        meeting.setMeetingId("MEET-OTHER-PM");
        meeting.setTitle("Cuoc hop cua PM khac");
        meeting.setOrganizerId(otherPmUser.getUserId());
        meeting.setStartTime(LocalDateTime.now().plusDays(13));
        meeting.setEndTime(LocalDateTime.now().plusDays(13).plusHours(1));
        meeting.setStatus("SCHEDULED");
        meetingRepository.saveAndFlush(meeting);

        US02MeetingRequest updateReq = new US02MeetingRequest(
                "PM 1 co sua",
                "Mo ta",
                meeting.getStartTime().plusHours(1),
                meeting.getEndTime().plusHours(1),
                null
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                us02MeetingService.update(meeting.getMeetingId(), pmUser.getUserId(), updateReq)
        );
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    @DisplayName("21. Admin sua cuoc hop cua PM khac -> Thanh cong")
    void test21_AdminModifiesOthersMeeting_Succeeds() {
        Meeting meeting = new Meeting();
        meeting.setMeetingId("MEET-ADMIN-EDIT");
        meeting.setTitle("Cuoc hop goc");
        meeting.setOrganizerId(pmUser.getUserId());
        meeting.setStartTime(LocalDateTime.now().plusDays(14));
        meeting.setEndTime(LocalDateTime.now().plusDays(14).plusHours(1));
        meeting.setStatus("SCHEDULED");
        meetingRepository.saveAndFlush(meeting);

        US02MeetingRequest updateReq = new US02MeetingRequest(
                "Admin can thiep sua",
                "Mo ta Admin",
                meeting.getStartTime().plusHours(1),
                meeting.getEndTime().plusHours(1),
                null
        );

        Meeting updated = us02MeetingService.update(meeting.getMeetingId(), adminUser1.getUserId(), updateReq);
        assertEquals("Admin can thiep sua", updated.getTitle());
    }

    @Test
    @DisplayName("22. RoomBookingService: PM dat phong cho cuoc hop cua chinh minh -> Thanh cong")
    void test22_PmBooksRoomForOwnMeeting_Succeeds() {
        SecurityContextHolder.getContext().setAuthentication(pmAuth);

        Meeting meeting = new Meeting();
        meeting.setMeetingId("MEET-ROOM-BOOK-1");
        meeting.setTitle("Hop can phong");
        meeting.setOrganizerId(pmUser.getUserId());
        meeting.setStartTime(LocalDateTime.now().plusDays(15));
        meeting.setEndTime(LocalDateTime.now().plusDays(15).plusHours(1));
        meeting.setStatus("SCHEDULED");
        meetingRepository.saveAndFlush(meeting);

        RoomBookingRequest req = new RoomBookingRequest();
        req.setMeetingId(meeting.getMeetingId());
        req.setRoomId(testRoom.getRoomId());

        RoomBookingResponse resp = roomBookingService.bookRoom(req);
        assertNotNull(resp);
        assertEquals(testRoom.getRoomId(), resp.getRoomId());
    }

    @Test
    @DisplayName("23. RoomBookingService: PM dat phong cho cuoc hop cua nguoi khac -> 403 Forbidden")
    void test23_PmBooksRoomForOthersMeeting_Throws403() {
        SecurityContextHolder.getContext().setAuthentication(pmAuth);

        Meeting meeting = new Meeting();
        meeting.setMeetingId("MEET-ROOM-BOOK-2");
        meeting.setTitle("Hop cua other PM");
        meeting.setOrganizerId(otherPmUser.getUserId());
        meeting.setStartTime(LocalDateTime.now().plusDays(16));
        meeting.setEndTime(LocalDateTime.now().plusDays(16).plusHours(1));
        meeting.setStatus("SCHEDULED");
        meetingRepository.saveAndFlush(meeting);

        RoomBookingRequest req = new RoomBookingRequest();
        req.setMeetingId(meeting.getMeetingId());
        req.setRoomId(testRoom.getRoomId());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                roomBookingService.bookRoom(req)
        );
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    // ==========================================
    // 6. ROLE CATEGORY UNIFIED MAPPING
    // ==========================================

    @Test
    @DisplayName("24. Kiem tra RoleCategory anh xa dung tat ca bien the ten vai tro")
    void test24_RoleCategoryMapping_HandlesAllVariants() {
        assertEquals(RoleCategory.ADMIN, RoleCategory.categorize("1", "Admin"));
        assertEquals(RoleCategory.ADMIN, RoleCategory.categorize("ROLE_ADMIN", "Administrator"));
        assertEquals(RoleCategory.ADMIN, RoleCategory.categorize("ADMIN", "Quản trị viên"));

        assertEquals(RoleCategory.PM, RoleCategory.categorize("2", "Người đặt lịch"));
        assertEquals(RoleCategory.PM, RoleCategory.categorize("PM", "PM"));
        assertEquals(RoleCategory.PM, RoleCategory.categorize("ROLE_PM", "Project Manager"));
        assertEquals(RoleCategory.PM, RoleCategory.categorize("BOOKER", "Booker"));

        assertEquals(RoleCategory.MEMBER, RoleCategory.categorize("3", "Người tham dự"));
        assertEquals(RoleCategory.MEMBER, RoleCategory.categorize("MEMBER", "Member"));
        assertEquals(RoleCategory.MEMBER, RoleCategory.categorize("ROLE_MEMBER", "Nhân viên"));
        assertEquals(RoleCategory.MEMBER, RoleCategory.categorize("USER", "Attendee"));

        assertNull(RoleCategory.categorize("UNKNOWN", "Unknown Role"));
    }

    // ==========================================
    // 7. LAST ADMIN LOCK & STATUS PROTECTION
    // ==========================================

    @Test
    @DisplayName("25. Khoa/vo hieu hoa Admin cuoi cung -> 409 Conflict")
    void test25_LockLastAdmin_Throws409Conflict() {
        SecurityContextHolder.getContext().setAuthentication(adminAuth);

        // adminUser2 bi khoa hoac chuyen role de adminUser1 la Admin duy nhat
        adminUser2.setStatus("LOCKED");
        userRepository.saveAndFlush(adminUser2);

        // Co gang khoa adminUser1 -> 409 Conflict
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                roleManagementService.updateUserStatus(adminUser1.getUserId(), "LOCKED")
        );
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("quan tri vien cuoi cung"));

        // Khoi phuc lai trang thai cho adminUser2
        adminUser2.setStatus("ACTIVE");
        userRepository.saveAndFlush(adminUser2);
    }

    @Test
    @DisplayName("26. Admin khoa nguoi dung binh thuong -> 200 OK va user bi khoa khong vao duoc he thong")
    void test26_AdminLockNormalUser_Succeeds() {
        SecurityContextHolder.getContext().setAuthentication(adminAuth);

        RoleManagementService.RoleResult result = roleManagementService.updateUserStatus(memberUser.getUserId(), "LOCKED");
        assertNotNull(result);

        User lockedMember = userRepository.findById(memberUser.getUserId()).orElseThrow();
        assertEquals("LOCKED", lockedMember.getStatus());

        // Kiem tra RbacAccess tu choi user vua bi khoa
        assertFalse(rbacAccess.canAttend(memberAuth));
        assertFalse(rbacAccess.canBook(memberAuth));

        // Khoi phuc
        lockedMember.setStatus("ACTIVE");
        userRepository.saveAndFlush(lockedMember);
    }
}
