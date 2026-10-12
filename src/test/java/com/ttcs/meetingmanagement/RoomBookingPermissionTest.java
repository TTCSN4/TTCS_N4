package com.ttcs.meetingmanagement;

import com.ttcs.meetingmanagement.repository.RoomRestrictionRepository;
import com.ttcs.meetingmanagement.room.Room;
import com.ttcs.meetingmanagement.room.RoomRepository;
import com.ttcs.meetingmanagement.service.RoomBookingPermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:meeting_perm_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class RoomBookingPermissionTest {

    @Autowired
    private RoomBookingPermissionService permissionService;

    @Autowired
    private RoomRestrictionRepository restrictionRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setup() {
        // Clean up and check schema
        roomRepository.deleteAll();
    }

    @Test
    void testMissingUserTableThrowsException() {
        // Room exists
        Room room = new Room();
        room.setRoomId("R-101");
        room.setRoomName("Phòng Họp 101");
        room.setCapacity(10);
        room.setStatus("AVAILABLE");
        roomRepository.save(room);

        // When user table does not exist, assertCanBook fails with BadSqlGrammarException
        assertThrows(Exception.class, () -> {
            permissionService.assertCanBook("U-001", "R-101");
        });
    }

    @Test
    void testErdSchemaColumnMissingInRoomRestriction() {
        // Create user and room tables according to ERD
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS `user` (" +
                "user_id VARCHAR(50) PRIMARY KEY, " +
                "role_id VARCHAR(50), " +
                "department_id VARCHAR(50), " +
                "hrm_code VARCHAR(50), " +
                "full_name VARCHAR(100), " +
                "email VARCHAR(100), " +
                "phone VARCHAR(20), " +
                "status VARCHAR(20)" +
                ")");

        // Create room_restriction strictly according to ERD:
        // restriction_id PK, room_id FK, user_id FK, notes
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS room_restriction (" +
                "restriction_id VARCHAR(50) PRIMARY KEY, " +
                "room_id VARCHAR(50), " +
                "user_id VARCHAR(50), " +
                "notes VARCHAR(255)" +
                ")");

        jdbcTemplate.execute("INSERT INTO `user` (user_id, full_name, status) VALUES ('U-001', 'Nguyen Van A', 'ACTIVE')");
        
        Room room = new Room();
        room.setRoomId("R-101");
        room.setRoomName("Phòng Họp 101");
        room.setCapacity(10);
        room.setStatus("AVAILABLE");
        roomRepository.save(room);

        // When room_restriction has a record
        jdbcTemplate.execute("INSERT INTO room_restriction (restriction_id, room_id, user_id, notes) " +
                "VALUES ('REST-1', 'R-101', 'U-001', 'Restricted from booking R-101')");

        // The query in RoomRestrictionRepository attempts to access rr.role_id and rr.department_id,
        // which do not exist in the ERD! This should throw BadSqlGrammarException!
        Exception ex = assertThrows(Exception.class, () -> {
            permissionService.assertCanBook("U-001", "R-101");
        });
        System.out.println("Exception with ERD schema: " + ex.getMessage());
    }

    @Test
    void testInvertedLogicBugDemonstration() {
        // If someone added role_id and department_id columns to room_restriction:
        jdbcTemplate.execute("DROP TABLE IF EXISTS room_restriction");
        jdbcTemplate.execute("DROP TABLE IF EXISTS `user`");

        jdbcTemplate.execute("CREATE TABLE `user` (" +
                "user_id VARCHAR(50) PRIMARY KEY, " +
                "role_id VARCHAR(50), " +
                "department_id VARCHAR(50), " +
                "full_name VARCHAR(100), " +
                "status VARCHAR(20)" +
                ")");

        jdbcTemplate.execute("CREATE TABLE room_restriction (" +
                "restriction_id VARCHAR(50) PRIMARY KEY, " +
                "room_id VARCHAR(50), " +
                "user_id VARCHAR(50), " +
                "role_id VARCHAR(50), " +
                "department_id VARCHAR(50), " +
                "notes VARCHAR(255)" +
                ")");

        jdbcTemplate.execute("INSERT INTO `user` (user_id, full_name, status) VALUES ('U-RESTRICTED', 'Restricted User', 'ACTIVE')");
        jdbcTemplate.execute("INSERT INTO `user` (user_id, full_name, status) VALUES ('U-NORMAL', 'Normal User', 'ACTIVE')");

        Room room = new Room();
        room.setRoomId("R-VIP");
        room.setRoomName("Phòng Họp VIP");
        room.setCapacity(20);
        room.setStatus("AVAILABLE");
        roomRepository.save(room);

        // Admin adds U-RESTRICTED to room_restriction for R-VIP
        jdbcTemplate.execute("INSERT INTO room_restriction (restriction_id, room_id, user_id, notes) " +
                "VALUES ('REST-1', 'R-VIP', 'U-RESTRICTED', 'Bị hạn chế đặt phòng VIP theo chính sách')");

        // BUG 1: The user who IS IN the restriction list CAN book! (assertDoesNotThrow)
        assertDoesNotThrow(() -> {
            permissionService.assertCanBook("U-RESTRICTED", "R-VIP");
        }, "LỖI LOGIC: Người dùng bị hạn chế (trong room_restriction) lại ĐƯỢC PHÉP đặt phòng!");

        // BUG 2: A normal user NOT in the restriction list CANNOT book! (throws 403 FORBIDDEN)
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
            permissionService.assertCanBook("U-NORMAL", "R-VIP");
        }, "LỖI LOGIC: Người dùng bình thường không bị hạn chế lại BỊ CHẶN (403 FORBIDDEN)!");

        assertEquals(403, ex.getStatusCode().value());
        System.out.println("Lỗi logic xác nhận: Người dùng bình thường bị chặn với thông báo: " + ex.getReason());
    }
}

