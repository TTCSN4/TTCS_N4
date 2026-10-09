package com.ttcs.meetingmanagement;

import com.ttcs.meetingmanagement.controller.MeetingController;
import com.ttcs.meetingmanagement.dto.CreateEquipmentRequest;
import com.ttcs.meetingmanagement.dto.CreateRoomRequest;
import com.ttcs.meetingmanagement.equipment.Equipment;
import com.ttcs.meetingmanagement.equipment.EquipmentController;
import com.ttcs.meetingmanagement.equipment.EquipmentRepository;
import com.ttcs.meetingmanagement.equipment.EquipmentService;
import com.ttcs.meetingmanagement.exception.GlobalExceptionHandler;
import com.ttcs.meetingmanagement.model.EquipmentBooking;
import com.ttcs.meetingmanagement.model.EquipmentBookingStatus;
import com.ttcs.meetingmanagement.model.Meeting;
import com.ttcs.meetingmanagement.model.MeetingStatus;
import com.ttcs.meetingmanagement.repository.EquipmentBookingRepository;
import com.ttcs.meetingmanagement.repository.MeetingRepository;
import com.ttcs.meetingmanagement.room.Room;
import com.ttcs.meetingmanagement.room.RoomRepository;
import com.ttcs.meetingmanagement.room.RoomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class WorkflowAndErdBugsTest {

    @Autowired
    private RoomService roomService;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private MeetingController meetingController;

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private EquipmentService equipmentService;

    @Autowired
    private EquipmentRepository equipmentRepository;

    @Autowired
    private EquipmentBookingRepository equipmentBookingRepository;

    @Autowired
    private EquipmentController equipmentController;

    @Autowired
    private GlobalExceptionHandler globalExceptionHandler;

    @BeforeEach
    void setUp() {
        equipmentBookingRepository.deleteAll();
        equipmentRepository.deleteAll();
        meetingRepository.deleteAll();
        roomRepository.deleteAll();
    }

    // =========================================================================
    // BUG 1: Lỗi câu lệnh SQL Native trong RoomRepository gây crash 500 khi xóa phòng
    // =========================================================================
    @Test
    @DisplayName("BUG 1: RoomRepository.countMeetingsByRoomId sai tên bảng 'meeting' và cột 'room_id' gây lỗi SQL")
    void testBug1_DeleteRoom_ThrowsSqlException_DueToWrongTableName() {
        CreateRoomRequest request = new CreateRoomRequest();
        request.setRoomId("ROOM-BUG-1");
        request.setRoomName("Phòng Bug 1");
        request.setCapacity(10);
        request.setStatus("AVAILABLE");
        roomService.createRoom(request);

        // Khi gọi deleteRoom, roomRepository.countMeetingsByRoomId() thực thi câu SQL:
        // SELECT COUNT(*) FROM meeting WHERE room_id = :roomId
        // Bảng thực tế là 'meetings' và cột là 'room', dẫn đến exception SQL từ H2
        Exception exception = assertThrows(Exception.class, () -> roomService.deleteRoom("ROOM-BUG-1"));
        String message = exception.getMessage() != null ? exception.getMessage().toLowerCase() : "";
        Throwable rootCause = exception;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }
        String rootMsg = rootCause.getMessage() != null ? rootCause.getMessage().toLowerCase() : "";

        assertTrue(message.contains("meeting") || rootMsg.contains("meeting") || rootMsg.contains("not found"),
                "Lỗi phát sinh do bảng 'meeting' không tồn tại trong database");
    }

    // =========================================================================
    // BUG 2: MeetingController.create KHÔNG kiểm tra trùng phòng họp
    // =========================================================================
    @Test
    @DisplayName("BUG 2: MeetingController.create cho phép 2 cuộc họp đặt trùng cùng 1 phòng tại cùng 1 thời điểm")
    void testBug2_CreateMeeting_AllowsDuplicateRoomBooking() {
        CreateRoomRequest roomReq = new CreateRoomRequest();
        roomReq.setRoomId("ROOM-DUPLICATE");
        roomReq.setRoomName("Phòng Thử Nghiệm Trùng Lịch");
        roomReq.setCapacity(20);
        roomReq.setStatus("AVAILABLE");
        roomService.createRoom(roomReq);

        OffsetDateTime start = OffsetDateTime.now().plusHours(2).withNano(0);
        OffsetDateTime end = start.plusHours(1);

        // Cuộc họp 1
        MeetingController.MeetingRequest m1 = new MeetingController.MeetingRequest(
                "Cuộc họp phòng ban A",
                "Mô tả A",
                start,
                end,
                1L,
                List.of("alice@company.com"),
                "NONE",
                0,
                "ROOM-DUPLICATE"
        );
        Meeting created1 = meetingController.create(m1);
        assertNotNull(created1.getId());

        // Cuộc họp 2: Khác người tham dự nhưng CÙNG PHÒNG và CÙNG KHUNG GIỜ
        MeetingController.MeetingRequest m2 = new MeetingController.MeetingRequest(
                "Cuộc họp phòng ban B",
                "Mô tả B",
                start,
                end,
                2L,
                List.of("bob@company.com"),
                "NONE",
                0,
                "ROOM-DUPLICATE"
        );

        // LỖI: Hệ thống chấp nhận cuộc họp 2 mà KHÔNG ném lỗi CONFLICT!
        Meeting created2 = meetingController.create(m2);
        assertNotNull(created2.getId());

        // Cả hai cuộc họp đều đang SCHEDULED trong cùng 1 phòng tại cùng 1 giờ
        assertEquals("ROOM-DUPLICATE", created1.getRoom());
        assertEquals("ROOM-DUPLICATE", created2.getRoom());
        assertEquals(created1.getStartTime(), created2.getStartTime());
        assertEquals(MeetingStatus.SCHEDULED, created1.getStatus());
        assertEquals(MeetingStatus.SCHEDULED, created2.getStatus());
    }

    // =========================================================================
    // BUG 3: GlobalExceptionHandler nuốt ResponseStatusException thành HTTP 500
    // =========================================================================
    @Test
    @DisplayName("BUG 3: GlobalExceptionHandler bắt generic Exception biến ResponseStatusException thành 500")
    void testBug3_GlobalExceptionHandler_ConvertsResponseStatusExceptionTo500() {
        ResponseStatusException notFound = new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy");
        ResponseEntity<Map<String, Object>> response = globalExceptionHandler.handleGenericException(notFound);

        // LỖI: Trả về HTTP 500 thay vì HTTP 404
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(500, response.getBody().get("status"));
    }

    // =========================================================================
    // BUG 4: Cho phép tạo cuộc họp trong quá khứ
    // =========================================================================
    @Test
    @DisplayName("BUG 4: MeetingController.create không kiểm tra thời gian bắt đầu trong quá khứ")
    void testBug4_CreateMeeting_InThePast_Allowed() {
        OffsetDateTime pastStart = OffsetDateTime.parse("2020-01-01T08:00:00+07:00");
        OffsetDateTime pastEnd = OffsetDateTime.parse("2020-01-01T09:00:00+07:00");

        MeetingController.MeetingRequest request = new MeetingController.MeetingRequest(
                "Cuộc họp quá khứ",
                "Mô tả",
                pastStart,
                pastEnd,
                1L,
                List.of("user@example.com"),
                "NONE",
                0,
                "P.101"
        );

        // LỖI: Vẫn lưu thành công một cuộc họp từ năm 2020
        Meeting created = meetingController.create(request);
        assertNotNull(created.getId());
        assertEquals(pastStart, created.getStartTime());
    }

    // =========================================================================
    // BUG 5: Bỏ qua kiểm tra sức chứa phòng (Capacity) so với số người tham gia
    // =========================================================================
    @Test
    @DisplayName("BUG 5: MeetingController không kiểm tra sức chứa phòng với số lượng người tham dự")
    void testBug5_CreateMeeting_ExceedsRoomCapacity() {
        CreateRoomRequest roomReq = new CreateRoomRequest();
        roomReq.setRoomId("ROOM-SMALL");
        roomReq.setRoomName("Phòng họp nhỏ");
        roomReq.setCapacity(2); // Sức chứa chỉ 2 người
        roomReq.setStatus("AVAILABLE");
        roomService.createRoom(roomReq);

        OffsetDateTime start = OffsetDateTime.now().plusDays(1).withNano(0);
        OffsetDateTime end = start.plusHours(1);

        // Đặt họp cho 5 người vào phòng chỉ chứa tối đa 2 người
        MeetingController.MeetingRequest request = new MeetingController.MeetingRequest(
                "Họp đông người",
                "Mô tả",
                start,
                end,
                1L,
                List.of("u1@ex.com", "u2@ex.com", "u3@ex.com", "u4@ex.com", "u5@ex.com"),
                "NONE",
                0,
                "ROOM-SMALL"
        );

        // LỖI: Vẫn cho phép đặt phòng thành công
        Meeting created = meetingController.create(request);
        assertNotNull(created.getId());
        assertTrue(created.getParticipants().size() > 2);
    }

    // =========================================================================
    // BUG 6: Tạo thiết bị gán vào phòng không tồn tại (Không ràng buộc khóa ngoại)
    // =========================================================================
    @Test
    @DisplayName("BUG 6: EquipmentService cho phép gán thiết bị vào roomId không hề tồn tại trong bảng Room")
    void testBug6_CreateEquipment_WithNonExistentRoom() {
        CreateEquipmentRequest eqReq = new CreateEquipmentRequest();
        eqReq.setRoomId("MA_PHONG_KHONG_HE_TON_TAI_9999");
        eqReq.setEquipmentName("Máy chiếu Test");
        eqReq.setType("PROJECTOR");
        eqReq.setTotalQuantity(1);
        eqReq.setStatus("AVAILABLE");

        // LỖI: Không kiểm tra roomRepository.existsById(), lưu thiết bị mồ côi roomId
        Equipment saved = equipmentService.createEquipment(eqReq);
        assertNotNull(saved.getEquipmentId());
        assertFalse(roomRepository.existsById(saved.getRoomId()));
    }

    // =========================================================================
    // BUG 7: Cập nhật cuộc họp tự động hồi sinh (resurrect) cuộc họp đã hủy
    // =========================================================================
    @Test
    @DisplayName("BUG 7: MeetingController.update tự động đổi status thành SCHEDULED cho cuộc họp đã CANCELLED")
    void testBug7_UpdateMeeting_ResurrectsCancelledMeeting() {
        OffsetDateTime start = OffsetDateTime.now().plusDays(2).withNano(0);
        OffsetDateTime end = start.plusHours(1);

        MeetingController.MeetingRequest createReq = new MeetingController.MeetingRequest(
                "Cuộc họp sắp hủy",
                "Mô tả",
                start,
                end,
                1L,
                List.of("user@ex.com"),
                "NONE",
                0,
                "P.101"
        );
        Meeting created = meetingController.create(createReq);

        // Hủy cuộc họp
        meetingController.cancel(created.getId());
        Meeting cancelledMeeting = meetingRepository.findById(created.getId()).orElseThrow();
        assertEquals(MeetingStatus.CANCELLED, cancelledMeeting.getStatus());

        // LỖI: Gọi update trên cuộc họp đã hủy -> cuộc họp bị đổi lại thành SCHEDULED!
        MeetingController.MeetingRequest updateReq = new MeetingController.MeetingRequest(
                "Cập nhật tiêu đề sau khi hủy",
                "Mô tả mới",
                start,
                end,
                1L,
                List.of("user@ex.com"),
                "NONE",
                0,
                "P.101"
        );
        Meeting updated = meetingController.update(created.getId(), updateReq);
        assertEquals(MeetingStatus.SCHEDULED, updated.getStatus());
    }

    // =========================================================================
    // BUG 8: Cập nhật thời gian/phòng của Meeting làm lệch pha với EquipmentBooking
    // =========================================================================
    @Test
    @DisplayName("BUG 8: Thay đổi giờ/phòng của Meeting không đồng bộ cập nhật EquipmentBooking liên kết")
    void testBug8_UpdateMeeting_DoesNotSyncEquipmentBookings() {
        CreateRoomRequest r1 = new CreateRoomRequest();
        r1.setRoomId("P.A");
        r1.setRoomName("Phòng A");
        r1.setCapacity(10);
        r1.setStatus("AVAILABLE");
        roomService.createRoom(r1);

        CreateRoomRequest r2 = new CreateRoomRequest();
        r2.setRoomId("P.B");
        r2.setRoomName("Phòng B");
        r2.setCapacity(10);
        r2.setStatus("AVAILABLE");
        roomService.createRoom(r2);

        CreateEquipmentRequest eqReq = new CreateEquipmentRequest();
        eqReq.setRoomId("P.A");
        eqReq.setEquipmentName("Mic A");
        eqReq.setType("MIC");
        eqReq.setTotalQuantity(2);
        eqReq.setStatus("AVAILABLE");
        Equipment eq = equipmentService.createEquipment(eqReq);

        OffsetDateTime start = OffsetDateTime.now().plusDays(3).withNano(0);
        OffsetDateTime end = start.plusHours(1);

        MeetingController.MeetingRequest createReq = new MeetingController.MeetingRequest(
                "Họp phòng A có mic",
                "Mô tả",
                start,
                end,
                1L,
                List.of("user@ex.com"),
                "NONE",
                0,
                "P.A"
        );
        Meeting meeting = meetingController.create(createReq);

        // Đặt thiết bị Mic A cho Meeting ở phòng P.A
        equipmentController.book(new EquipmentController.EquipmentBookingRequest(
                eq.getEquipmentId(),
                meeting.getId(),
                "P.A",
                1,
                start,
                end
        ));

        // Bây giờ sửa Meeting chuyển sang phòng P.B và dời giờ sang chiều
        OffsetDateTime newStart = start.plusHours(4);
        OffsetDateTime newEnd = end.plusHours(4);
        MeetingController.MeetingRequest updateReq = new MeetingController.MeetingRequest(
                "Họp đổi sang phòng B",
                "Mô tả",
                newStart,
                newEnd,
                1L,
                List.of("user@ex.com"),
                "NONE",
                0,
                "P.B"
        );
        meetingController.update(meeting.getId(), updateReq);

        // LỖI: EquipmentBooking vẫn lưu giờ cũ và phòng cũ P.A
        List<EquipmentBooking> bookings = equipmentBookingRepository.findAllByMeetingId(meeting.getId());
        assertEquals(1, bookings.size());
        EquipmentBooking booking = bookings.get(0);
        assertEquals("P.A", booking.getRoom()); // Vẫn là phòng P.A dù meeting đã sang P.B!
        assertEquals(start, booking.getStartTime()); // Vẫn là giờ cũ!
    }

    // =========================================================================
    // BUG 9: Kiểm tra sự vắng mặt của các Entity/Luồng bắt buộc theo sơ đồ ERD
    // =========================================================================
    @Test
    @DisplayName("BUG 9: Toàn bộ các Entity nghiệp vụ trong ERD không hề có trong hệ thống")
    void testBug9_MissingEntitiesFromErdDiagram() {
        // Kiểm tra xem các class đại diện cho ERD có tồn tại trong classpath hay không
        assertThrows(ClassNotFoundException.class, () -> Class.forName("com.ttcs.meetingmanagement.model.Department"),
                "Entity DEPARTMENT từ ERD hoàn toàn thiếu");
        assertThrows(ClassNotFoundException.class, () -> Class.forName("com.ttcs.meetingmanagement.model.Role"),
                "Entity ROLE từ ERD hoàn toàn thiếu");
        assertThrows(ClassNotFoundException.class, () -> Class.forName("com.ttcs.meetingmanagement.model.User"),
                "Entity USER từ ERD hoàn toàn thiếu");
        assertThrows(ClassNotFoundException.class, () -> Class.forName("com.ttcs.meetingmanagement.model.CheckInLog"),
                "Entity CHECK_IN_LOG từ ERD hoàn toàn thiếu");
        assertThrows(ClassNotFoundException.class, () -> Class.forName("com.ttcs.meetingmanagement.model.Notification"),
                "Entity NOTIFICATION từ ERD hoàn toàn thiếu");
        assertThrows(ClassNotFoundException.class, () -> Class.forName("com.ttcs.meetingmanagement.model.CalendarSync"),
                "Entity CALENDAR_SYNC từ ERD hoàn toàn thiếu");
        assertThrows(ClassNotFoundException.class, () -> Class.forName("com.ttcs.meetingmanagement.model.RoomRestriction"),
                "Entity ROOM_RESTRICTION từ ERD hoàn toàn thiếu");
        assertThrows(ClassNotFoundException.class, () -> Class.forName("com.ttcs.meetingmanagement.model.MeetingEquipment"),
                "Entity MEETING_EQUIPMENT từ ERD hoàn toàn thiếu");
    }

    // =========================================================================
    // BUG 10: Hủy cuộc họp không ghi nhận cancel_reason, cancelled_at và không nhả room
    // =========================================================================
    @Test
    @DisplayName("BUG 10: MeetingController.cancel không lưu lý do hủy, thời điểm hủy và không giải phóng room")
    void testBug10_CancelMeeting_MissingCancelAuditFields() {
        OffsetDateTime start = OffsetDateTime.now().plusDays(5).withNano(0);
        OffsetDateTime end = start.plusHours(1);

        Meeting created = meetingController.create(new MeetingController.MeetingRequest(
                "Họp sẽ hủy", "Mô tả", start, end, 1L, List.of("dev@company.com"), "NONE", 0, "P.101"
        ));

        // Hủy cuộc họp
        meetingController.cancel(created.getId());
        Meeting cancelled = meetingRepository.findById(created.getId()).orElseThrow();

        // LỖI: Thuộc tính room vẫn giữ nguyên là "P.101"
        assertEquals("P.101", cancelled.getRoom());
        // Và trong Entity Meeting hoàn toàn không có trường cancelReason hay cancelledAt theo ERD
    }

    // =========================================================================
    // BUG 11: RoomService.filterByParticipantCount từ chối giá trị <= 0
    // =========================================================================
    @Test
    @DisplayName("BUG 11: RoomService.filterByParticipantCount ném RoomException khi participantCount <= 0")
    void testBug11_FilterByParticipantCount_InvalidValue() {
        assertThrows(RuntimeException.class, () -> roomService.filterByParticipantCount(0));
        assertThrows(RuntimeException.class, () -> roomService.filterByParticipantCount(-5));
    }

    // =========================================================================
    // USER STORY: KIỂM TRA TRẠNG THÁI THIẾT BỊ VÀ CHẶN ĐẶT TRÙNG (EQUIPMENT CONFLICT)
    // =========================================================================

    @Test
    @DisplayName("TC_EQ_01: Chặn đặt trùng thiết bị khi đã hết số lượng khả dụng trong khung giờ (Overlapping conflict)")
    void testTC_EQ_01_RejectEquipmentBooking_WhenExceedingCapacityInSlot() {
        CreateRoomRequest roomReq = new CreateRoomRequest();
        roomReq.setRoomId("P.EQ1");
        roomReq.setRoomName("Phòng Thiết Bị 1");
        roomReq.setCapacity(10);
        roomReq.setStatus("AVAILABLE");
        roomService.createRoom(roomReq);

        CreateEquipmentRequest eqReq = new CreateEquipmentRequest();
        eqReq.setRoomId("P.EQ1");
        eqReq.setEquipmentName("Máy chiếu Panasonic");
        eqReq.setType("PROJECTOR");
        eqReq.setTotalQuantity(1); // Chỉ có đúng 1 máy chiếu
        eqReq.setStatus("AVAILABLE");
        Equipment eq = equipmentService.createEquipment(eqReq);

        OffsetDateTime start = OffsetDateTime.now().plusDays(3).withNano(0);
        OffsetDateTime end = start.plusHours(2);

        // Lượt đặt 1: từ start đến end (chiếm trọn 1 máy chiếu)
        equipmentController.book(new EquipmentController.EquipmentBookingRequest(
                eq.getEquipmentId(), null, "P.EQ1", 1, start, end
        ));

        // Lượt đặt 2: từ start + 30 phút đến end + 30 phút (trùng một phần khung giờ)
        OffsetDateTime overlapStart = start.plusMinutes(30);
        OffsetDateTime overlapEnd = end.plusMinutes(30);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                equipmentController.book(new EquipmentController.EquipmentBookingRequest(
                        eq.getEquipmentId(), null, "P.EQ1", 1, overlapStart, overlapEnd
                ))
        );
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Không đủ thiết bị khả dụng"));
    }

    @Test
    @DisplayName("TC_EQ_02: Cho phép 2 lượt đặt thiết bị nối tiếp nhau theo khoảng nửa mở [start, end) mà không bị coi là trùng")
    void testTC_EQ_02_AllowConsecutiveEquipmentBookings() {
        CreateRoomRequest roomReq = new CreateRoomRequest();
        roomReq.setRoomId("P.EQ2");
        roomReq.setRoomName("Phòng Thiết Bị 2");
        roomReq.setCapacity(10);
        roomReq.setStatus("AVAILABLE");
        roomService.createRoom(roomReq);

        CreateEquipmentRequest eqReq = new CreateEquipmentRequest();
        eqReq.setRoomId("P.EQ2");
        eqReq.setEquipmentName("Loa Bluetooth");
        eqReq.setType("SPEAKER");
        eqReq.setTotalQuantity(1);
        eqReq.setStatus("AVAILABLE");
        Equipment eq = equipmentService.createEquipment(eqReq);

        OffsetDateTime t1 = OffsetDateTime.now().plusDays(4).withNano(0);
        OffsetDateTime t2 = t1.plusHours(1);
        OffsetDateTime t3 = t2.plusHours(1);

        // Lượt đặt 1: [t1, t2)
        EquipmentController.BookingResponse b1 = equipmentController.book(new EquipmentController.EquipmentBookingRequest(
                eq.getEquipmentId(), null, "P.EQ2", 1, t1, t2
        ));
        assertNotNull(b1.id());

        // Lượt đặt 2: [t2, t3) - nối tiếp ngay sau lượt 1, không bị coi là trùng!
        EquipmentController.BookingResponse b2 = equipmentController.book(new EquipmentController.EquipmentBookingRequest(
                eq.getEquipmentId(), null, "P.EQ2", 1, t2, t3
        ));
        assertNotNull(b2.id());
    }

    @Test
    @DisplayName("TC_EQ_03 (BUG): Phần mềm KHÔNG kiểm tra trạng thái thiết bị 'BROKEN' trong danh mục trước khi cho đặt")
    void testTC_EQ_03_Bug_AllowsBookingBrokenEquipment() {
        CreateRoomRequest roomReq = new CreateRoomRequest();
        roomReq.setRoomId("P.EQ3");
        roomReq.setRoomName("Phòng Thiết Bị 3");
        roomReq.setCapacity(10);
        roomReq.setStatus("AVAILABLE");
        roomService.createRoom(roomReq);

        // Thiết bị có trạng thái BROKEN (hỏng hóc) ngay từ danh mục
        CreateEquipmentRequest eqReq = new CreateEquipmentRequest();
        eqReq.setRoomId("P.EQ3");
        eqReq.setEquipmentName("Bảng thông minh bị hỏng cảm ứng");
        eqReq.setType("SMART_BOARD");
        eqReq.setTotalQuantity(1);
        eqReq.setStatus("BROKEN");
        Equipment brokenEq = equipmentService.createEquipment(eqReq);
        assertEquals("BROKEN", brokenEq.getStatus());

        OffsetDateTime start = OffsetDateTime.now().plusDays(5).withNano(0);
        OffsetDateTime end = start.plusHours(1);

        // LỖI NGHIỆP VỤ: Theo User Story, thiết bị hỏng/bảo trì thì không được cho đặt.
        // Nhưng EquipmentController.book() chỉ kiểm tra capacity mà bỏ qua brokenEq.getStatus()!
        EquipmentController.BookingResponse booking = equipmentController.book(new EquipmentController.EquipmentBookingRequest(
                brokenEq.getEquipmentId(), null, "P.EQ3", 1, start, end
        ));

        // Chứng minh lỗi: Hệ thống vẫn cho đặt thành công thiết bị đang BROKEN!
        assertNotNull(booking.id());
        assertEquals(EquipmentBookingStatus.BOOKED, booking.status());
    }

    @Test
    @DisplayName("TC_EQ_04: Tra cứu trạng thái thiết bị thời gian thực phản ánh đúng BOOKED và AVAILABLE")
    void testTC_EQ_04_EquipmentStatusQueryReflectsBookedAndAvailable() {
        CreateRoomRequest roomReq = new CreateRoomRequest();
        roomReq.setRoomId("P.EQ4");
        roomReq.setRoomName("Phòng Thiết Bị 4");
        roomReq.setCapacity(10);
        roomReq.setStatus("AVAILABLE");
        roomService.createRoom(roomReq);

        CreateEquipmentRequest eqReq = new CreateEquipmentRequest();
        eqReq.setRoomId("P.EQ4");
        eqReq.setEquipmentName("Bộ Mic không dây");
        eqReq.setType("MIC");
        eqReq.setTotalQuantity(2); // Có 2 bộ mic
        eqReq.setStatus("AVAILABLE");
        Equipment eq = equipmentService.createEquipment(eqReq);

        OffsetDateTime start = OffsetDateTime.now().plusDays(6).withNano(0);
        OffsetDateTime end = start.plusHours(2);
        OffsetDateTime mid = start.plusHours(1);

        // Đặt cả 2 bộ mic trong khung giờ [start, end)
        equipmentController.book(new EquipmentController.EquipmentBookingRequest(
                eq.getEquipmentId(), null, "P.EQ4", 2, start, end
        ));

        // Tra cứu trạng thái tại thời điểm mid (giữa giờ họp)
        List<EquipmentController.EquipmentStatusResponse> statuses = equipmentController.status("P.EQ4", mid);
        assertEquals(1, statuses.size());
        EquipmentController.EquipmentStatusResponse status = statuses.get(0);

        assertEquals(2, status.totalQuantity());
        assertEquals(2, status.bookedQuantity());
        assertEquals(0, status.availableQuantity()); // Đã hết khả dụng
        assertTrue(status.statuses().contains(com.ttcs.meetingmanagement.model.EquipmentAvailabilityStatus.BOOKED));
        assertFalse(status.statuses().contains(com.ttcs.meetingmanagement.model.EquipmentAvailabilityStatus.AVAILABLE));
    }
}
