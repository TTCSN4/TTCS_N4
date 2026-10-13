package com.ttcs.meetingmanagement;

import com.ttcs.meetingmanagement.dto.CancelRoomBookingRequest;
import com.ttcs.meetingmanagement.dto.RoomBookingRequest;
import com.ttcs.meetingmanagement.dto.RoomBookingResponse;
import com.ttcs.meetingmanagement.equipment.Equipment;
import com.ttcs.meetingmanagement.equipment.EquipmentController;
import com.ttcs.meetingmanagement.equipment.EquipmentRepository;
import com.ttcs.meetingmanagement.model.EquipmentBooking;
import com.ttcs.meetingmanagement.model.EquipmentBookingStatus;
import com.ttcs.meetingmanagement.model.Meeting;
import com.ttcs.meetingmanagement.model.MeetingStatus;
import com.ttcs.meetingmanagement.repository.EquipmentBookingRepository;
import com.ttcs.meetingmanagement.repository.MeetingRepository;
import com.ttcs.meetingmanagement.room.Room;
import com.ttcs.meetingmanagement.room.RoomRepository;
import com.ttcs.meetingmanagement.roombooking.RoomBookingController;
import com.ttcs.meetingmanagement.roombooking.RoomBookingService;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RoomBookingCancelTests {

    @Autowired
    private RoomBookingController roomBookingController;

    @Autowired
    private RoomBookingService roomBookingService;

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private EquipmentRepository equipmentRepository;

    @Autowired
    private EquipmentBookingRepository equipmentBookingRepository;

    @Autowired
    private EquipmentController equipmentController;

    @Test
    @Transactional
    @DisplayName("TC01: Hủy phòng có 2 thiết bị đi kèm -> Phòng và đúng 2 thiết bị được giải phóng")
    void tc01_cancelRoomWithTwoEquipments_releasesRoomAndBothEquipments() {
        Room room = createRoom("TC01-R1");
        OffsetDateTime start = OffsetDateTime.parse("2030-08-01T09:00:00+07:00");
        OffsetDateTime end = start.plusHours(1);
        Meeting meeting = createMeeting(room.getRoomId(), start, end);

        Equipment eq1 = createEquipment(room.getRoomId(), "Projector 1", 2);
        Equipment eq2 = createEquipment(room.getRoomId(), "Mic 1", 2);

        createEquipmentBooking(eq1, meeting.getId(), room.getRoomId(), 1, start, end, EquipmentBookingStatus.BOOKED);
        createEquipmentBooking(eq2, meeting.getId(), room.getRoomId(), 1, start, end, EquipmentBookingStatus.BOOKED);

        // Trước khi hủy: thiết bị bị chiếm dụng
        var statusBefore = equipmentController.status(room.getRoomId(), start.plusMinutes(10));
        assertEquals(1, statusBefore.stream().filter(e -> e.equipmentId().equals(eq1.getEquipmentId())).findFirst().orElseThrow().bookedQuantity());

        // Hủy đặt phòng
        ResponseEntity<RoomBookingResponse> response = roomBookingController.cancelRoomBooking(
                meeting.getId(),
                "Hủy phòng do đổi kế hoạch",
                null
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("TC01-R1", response.getBody().getRoomId());
        assertEquals("Hủy phòng do đổi kế hoạch", response.getBody().getCancelReason());
        assertNotNull(response.getBody().getCancelledAt());

        // Kiểm tra Meeting: room = null, cancel_reason & cancelled_at đã lưu
        Meeting updatedMeeting = meetingRepository.findById(meeting.getId()).orElseThrow();
        assertNull(updatedMeeting.getRoom());
        assertEquals("Hủy phòng do đổi kế hoạch", updatedMeeting.getCancelReason());
        assertNotNull(updatedMeeting.getCancelledAt());

        // Kiểm tra thiết bị: cả 2 bản ghi đều chuyển sang CANCELLED
        List<EquipmentBooking> bookings = equipmentBookingRepository.findAllByMeetingId(meeting.getId());
        assertEquals(2, bookings.size());
        assertTrue(bookings.stream().allMatch(b -> b.getStatus() == EquipmentBookingStatus.CANCELLED));

        // Kiểm tra số lượng tồn kho: không còn bị chiếm dụng ảo
        var statusAfter = equipmentController.status(room.getRoomId(), start.plusMinutes(10));
        assertEquals(0, statusAfter.stream().filter(e -> e.equipmentId().equals(eq1.getEquipmentId())).findFirst().orElseThrow().bookedQuantity());
        assertEquals(2, statusAfter.stream().filter(e -> e.equipmentId().equals(eq1.getEquipmentId())).findFirst().orElseThrow().availableQuantity());
    }

    @Test
    @Transactional
    @DisplayName("TC02: Hủy phòng không gán thiết bị -> Hủy hợp lệ, không lỗi null, lưu metadata hủy")
    void tc02_cancelRoomWithoutEquipment_success() {
        Room room = createRoom("TC02-R1");
        OffsetDateTime start = OffsetDateTime.parse("2030-08-02T09:00:00+07:00");
        OffsetDateTime end = start.plusHours(1);
        Meeting meeting = createMeeting(room.getRoomId(), start, end);

        ResponseEntity<RoomBookingResponse> response = roomBookingController.cancelRoomBooking(
                meeting.getId(),
                null,
                new CancelRoomBookingRequest("Chuyển sang họp online")
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Meeting updated = meetingRepository.findById(meeting.getId()).orElseThrow();
        assertNull(updated.getRoom());
        assertEquals("Chuyển sang họp online", updated.getCancelReason());
        assertNotNull(updated.getCancelledAt());
    }

    @Test
    @Transactional
    @DisplayName("TC03: Hủy có lý do hợp lệ -> Có cancel_reason và cancelled_at đúng bản ghi, đúng thời gian")
    void tc03_cancelWithValidReason_persistsMetadataCorrectly() {
        Room room = createRoom("TC03-R1");
        OffsetDateTime start = OffsetDateTime.parse("2030-08-03T14:00:00+07:00");
        OffsetDateTime end = start.plusHours(2);
        Meeting meeting = createMeeting(room.getRoomId(), start, end);

        OffsetDateTime beforeCancel = OffsetDateTime.now();
        roomBookingController.cancelRoomBooking(
                meeting.getId(),
                "Khách báo hoãn buổi họp",
                null
        );
        OffsetDateTime afterCancel = OffsetDateTime.now();

        Meeting updated = meetingRepository.findById(meeting.getId()).orElseThrow();
        assertEquals("Khách báo hoãn buổi họp", updated.getCancelReason());
        assertNotNull(updated.getCancelledAt());
        assertTrue(!updated.getCancelledAt().isBefore(beforeCancel) && !updated.getCancelledAt().isAfter(afterCancel));
    }

    @Test
    @Transactional
    @DisplayName("TC04: Hủy ID không tồn tại -> Trả 404 NOT_FOUND")
    void tc04_cancelNonExistentMeeting_throwsNotFound() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                roomBookingController.cancelRoomBooking(999999L, "Reason", null)
        );
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    @Transactional
    @DisplayName("TC05: Hủy lần 2 cùng bản ghi -> Trả 400 BAD_REQUEST vì không còn phòng để hủy")
    void tc05_cancelSecondTime_throwsBadRequest() {
        Room room = createRoom("TC05-R1");
        OffsetDateTime start = OffsetDateTime.parse("2030-08-04T09:00:00+07:00");
        OffsetDateTime end = start.plusHours(1);
        Meeting meeting = createMeeting(room.getRoomId(), start, end);

        // Hủy lần 1 thành công
        roomBookingController.cancelRoomBooking(meeting.getId(), "Lần 1", null);

        // Hủy lần 2 báo lỗi
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                roomBookingController.cancelRoomBooking(meeting.getId(), "Lần 2", null)
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("no room booking to cancel"));
    }

    @Test
    @Transactional
    @DisplayName("TC06: Giao dịch có rollback khi gặp ngoại lệ -> không hủy dở dang")
    void tc06_transactionalRollback_preservesStateOnFailure() {
        // Kiểm tra logic Transactional của service: khi meeting không hợp lệ, dữ liệu không bị thay đổi
        Room room = createRoom("TC06-R1");
        OffsetDateTime start = OffsetDateTime.parse("2030-08-05T09:00:00+07:00");
        OffsetDateTime end = start.plusHours(1);
        Meeting meeting = createMeeting(room.getRoomId(), start, end);

        assertThrows(ResponseStatusException.class, () ->
                roomBookingService.cancelRoomBooking(-1L, "Fail")
        );

        Meeting preserved = meetingRepository.findById(meeting.getId()).orElseThrow();
        assertEquals(room.getRoomId(), preserved.getRoom());
        assertNull(preserved.getCancelReason());
    }

    @Test
    @Transactional
    @DisplayName("TC07: Hủy meeting A, giữ meeting B -> Thiết bị/phòng của B không bị ảnh hưởng")
    void tc07_cancelMeetingA_doesNotAffectMeetingB() {
        Room room = createRoom("TC07-R1");
        OffsetDateTime startA = OffsetDateTime.parse("2030-08-06T09:00:00+07:00");
        OffsetDateTime endA = startA.plusHours(1);
        Meeting meetingA = createMeeting(room.getRoomId(), startA, endA);

        OffsetDateTime startB = OffsetDateTime.parse("2030-08-06T10:00:00+07:00");
        OffsetDateTime endB = startB.plusHours(1);
        Meeting meetingB = createMeeting(room.getRoomId(), startB, endB);

        Equipment eq = createEquipment(room.getRoomId(), "Loa Bluetooth", 5);
        EquipmentBooking bookingA = createEquipmentBooking(eq, meetingA.getId(), room.getRoomId(), 2, startA, endA, EquipmentBookingStatus.BOOKED);
        EquipmentBooking bookingB = createEquipmentBooking(eq, meetingB.getId(), room.getRoomId(), 2, startB, endB, EquipmentBookingStatus.BOOKED);

        // Hủy Meeting A
        roomBookingController.cancelRoomBooking(meetingA.getId(), "Hủy A", null);

        // Meeting A giải phóng
        assertNull(meetingRepository.findById(meetingA.getId()).orElseThrow().getRoom());
        assertEquals(EquipmentBookingStatus.CANCELLED, equipmentBookingRepository.findById(bookingA.getId()).orElseThrow().getStatus());

        // Meeting B vẫn nguyên vẹn
        Meeting bReloaded = meetingRepository.findById(meetingB.getId()).orElseThrow();
        assertEquals(room.getRoomId(), bReloaded.getRoom());
        assertEquals(EquipmentBookingStatus.BOOKED, equipmentBookingRepository.findById(bookingB.getId()).orElseThrow().getStatus());
    }

    @Test
    @Transactional
    @DisplayName("TC08: Đặt lại phòng/thiết bị vừa hủy (Regression US08, US12) -> Thành công, không bị xung đột")
    void tc08_rebookRoomAndEquipmentAfterCancellation_success() {
        Room room = createRoom("TC08-R1");
        OffsetDateTime start = OffsetDateTime.parse("2030-08-07T09:00:00+07:00");
        OffsetDateTime end = start.plusHours(1);
        Meeting meeting1 = createMeeting(room.getRoomId(), start, end);

        Equipment eq = createEquipment(room.getRoomId(), "Bảng điện tử", 1);
        createEquipmentBooking(eq, meeting1.getId(), room.getRoomId(), 1, start, end, EquipmentBookingStatus.BOOKED);

        // Cuộc họp 2 muốn đặt cùng phòng/giờ: bị xung đột
        Meeting meeting2 = createMeeting(null, start, end);
        RoomBookingRequest bookRequest = new RoomBookingRequest();
        bookRequest.setMeetingId(meeting2.getId());
        bookRequest.setRoomId(room.getRoomId());

        ResponseStatusException conflictEx = assertThrows(ResponseStatusException.class, () ->
                roomBookingService.bookRoom(bookRequest)
        );
        assertEquals(HttpStatus.CONFLICT, conflictEx.getStatusCode());

        // Hủy đặt phòng cuộc họp 1
        roomBookingController.cancelRoomBooking(meeting1.getId(), "Nhường phòng", null);

        // Giờ cuộc họp 2 đặt phòng thành công (US08 regression)
        RoomBookingResponse bookedResponse = roomBookingService.bookRoom(bookRequest);
        assertNotNull(bookedResponse);
        assertEquals(room.getRoomId(), bookedResponse.getRoomId());

        // Cuộc họp 2 đặt thiết bị thành công (US12 regression)
        var eqBookingResponse = equipmentController.book(new EquipmentController.EquipmentBookingRequest(
                eq.getEquipmentId(),
                meeting2.getId(),
                room.getRoomId(),
                1,
                start,
                end
        ));
        assertNotNull(eqBookingResponse);
        assertEquals(EquipmentBookingStatus.BOOKED, eqBookingResponse.status());
    }

    private Room createRoom(String roomId) {
        Room room = new Room();
        room.setRoomId(roomId);
        room.setRoomName("Phòng " + roomId);
        room.setCapacity(20);
        room.setLocation("Tầng 2");
        room.setStatus("AVAILABLE");
        return roomRepository.save(room);
    }

    private Meeting createMeeting(String roomId, OffsetDateTime start, OffsetDateTime end) {
        Meeting meeting = new Meeting();
        meeting.setTitle("Cuộc họp thử nghiệm " + UUID.randomUUID());
        meeting.setRoom(roomId);
        meeting.setStartTime(start);
        meeting.setEndTime(end);
        meeting.setOrganizerId(1L);
        meeting.setStatus(MeetingStatus.SCHEDULED);
        meeting.setCreatedAt(OffsetDateTime.now());
        meeting.setUpdatedAt(OffsetDateTime.now());
        return meetingRepository.save(meeting);
    }

    private Equipment createEquipment(String roomId, String name, int quantity) {
        Equipment equipment = new Equipment();
        equipment.setEquipmentId("EQ-" + UUID.randomUUID());
        equipment.setRoomId(roomId);
        equipment.setEquipmentName(name);
        equipment.setType("PROJECTOR");
        equipment.setTotalQuantity(quantity);
        equipment.setStatus("AVAILABLE");
        return equipmentRepository.save(equipment);
    }

    private EquipmentBooking createEquipmentBooking(
            Equipment equipment,
            Long meetingId,
            String room,
            int quantity,
            OffsetDateTime start,
            OffsetDateTime end,
            EquipmentBookingStatus status
    ) {
        EquipmentBooking booking = new EquipmentBooking();
        booking.setEquipment(equipment);
        booking.setMeetingId(meetingId);
        booking.setRoom(room);
        booking.setQuantity(quantity);
        booking.setStartTime(start);
        booking.setEndTime(end);
        booking.setStatus(status);
        return equipmentBookingRepository.save(booking);
    }
}
