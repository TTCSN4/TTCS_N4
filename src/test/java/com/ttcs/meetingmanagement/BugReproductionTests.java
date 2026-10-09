package com.ttcs.meetingmanagement;

import com.ttcs.meetingmanagement.controller.MeetingController;
import com.ttcs.meetingmanagement.dto.CreateEquipmentRequest;
import com.ttcs.meetingmanagement.dto.CreateRoomRequest;
import com.ttcs.meetingmanagement.dto.RoomBookingRequest;
import com.ttcs.meetingmanagement.dto.RoomBookingResponse;
import com.ttcs.meetingmanagement.equipment.Equipment;
import com.ttcs.meetingmanagement.equipment.EquipmentController;
import com.ttcs.meetingmanagement.equipment.EquipmentRepository;
import com.ttcs.meetingmanagement.equipment.EquipmentService;
import com.ttcs.meetingmanagement.exception.GlobalExceptionHandler;
import com.ttcs.meetingmanagement.model.Meeting;
import com.ttcs.meetingmanagement.model.MeetingStatus;
import com.ttcs.meetingmanagement.repository.MeetingRepository;
import com.ttcs.meetingmanagement.room.Room;
import com.ttcs.meetingmanagement.room.RoomRepository;
import com.ttcs.meetingmanagement.room.RoomService;
import com.ttcs.meetingmanagement.roombooking.RoomBookingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class BugReproductionTests {

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private RoomService roomService;

    @Autowired
    private MeetingController meetingController;

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private EquipmentService equipmentService;

    @Autowired
    private EquipmentRepository equipmentRepository;

    @Autowired
    private EquipmentController equipmentController;

    @Autowired
    private RoomBookingService roomBookingService;

    @Autowired
    private GlobalExceptionHandler globalExceptionHandler;

    @Test
    @DisplayName("Bug 1: countMeetingsByRoomId fails due to SQL syntax/table name mismatch (table 'meeting' vs 'meetings', 'room_id' vs 'room')")
    void testCountMeetingsByRoomIdSqlFailure() {
        assertThrows(DataAccessException.class, () -> {
            roomRepository.countMeetingsByRoomId("P.101");
        }, "Calling countMeetingsByRoomId must fail with DataAccessException because table 'meeting' and column 'room_id' do not match JPA entity table 'meetings' and column 'room'");
    }

    @Test
    @DisplayName("Bug 2: MeetingController allows double-booking the same room at the same time if participants do not overlap")
    void testRoomDoubleBookingLogicConflict() {
        OffsetDateTime start = OffsetDateTime.parse("2030-10-15T14:00:00+07:00");
        OffsetDateTime end = OffsetDateTime.parse("2030-10-15T15:00:00+07:00");

        MeetingController.MeetingRequest req1 = new MeetingController.MeetingRequest(
                "Meeting 1", "Desc 1", start, end, 1L,
                List.of("user1@example.com"), "NONE", 0, "P.101"
        );
        Meeting m1 = meetingController.create(req1);
        assertNotNull(m1.getId());

        MeetingController.MeetingRequest req2 = new MeetingController.MeetingRequest(
                "Meeting 2", "Desc 2", start, end, 2L,
                List.of("user2@example.com"), "NONE", 0, "P.101"
        );

        // This SHOULD throw 409 CONFLICT because P.101 is already booked!
        // But MeetingController only checks participant conflict, so it succeeds!
        Meeting m2 = meetingController.create(req2);
        assertNotNull(m2.getId(), "BUG CONFIRMED: Both meetings were created in P.101 at the same time without any conflict error!");
        assertEquals(m1.getRoom(), m2.getRoom());
    }

    @Test
    @DisplayName("Bug 3: GlobalExceptionHandler catches ResponseStatusException under Exception.class and returns 500 instead of 404/409")
    void testGlobalExceptionHandlerMasksResponseStatusException() {
        ResponseStatusException notFoundEx = new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy");
        ResponseEntity<Map<String, Object>> response = globalExceptionHandler.handleGenericException(notFoundEx);
        
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode(),
                "BUG CONFIRMED: GlobalExceptionHandler converts ResponseStatusException into 500 Internal Server Error!");
        assertEquals(500, response.getBody().get("status"));
    }

    @Test
    @DisplayName("Bug 4: Equipment can be created with a non-existent roomId due to lack of FK/validation")
    void testEquipmentCreatedWithNonExistentRoom() {
        CreateEquipmentRequest req = new CreateEquipmentRequest();
        req.setEquipmentName("Máy chiếu ảo");
        req.setRoomId("ROOM_DOES_NOT_EXIST_9999");
        req.setType("PROJECTOR");
        req.setTotalQuantity(2);
        req.setStatus("AVAILABLE");

        Equipment created = equipmentService.createEquipment(req);
        assertNotNull(created.getEquipmentId(), "BUG CONFIRMED: Equipment created with fake non-existent roomId!");
        assertFalse(roomRepository.existsById("ROOM_DOES_NOT_EXIST_9999"));
    }

    @Test
    @DisplayName("Bug 5: Meeting update revives a CANCELLED meeting back to SCHEDULED without checking conflicts")
    void testMeetingUpdateRevivesCancelledMeeting() {
        OffsetDateTime start = OffsetDateTime.parse("2030-11-20T08:00:00+07:00");
        OffsetDateTime end = OffsetDateTime.parse("2030-11-20T09:00:00+07:00");

        MeetingController.MeetingRequest req = new MeetingController.MeetingRequest(
                "Revive Meeting", "Desc", start, end, 1L,
                List.of("revive@example.com"), "NONE", 0, "P.102"
        );
        Meeting m = meetingController.create(req);
        meetingController.cancel(m.getId());
        assertEquals(MeetingStatus.CANCELLED, meetingRepository.findById(m.getId()).get().getStatus());

        // Update the meeting
        meetingController.update(m.getId(), req);
        Meeting updated = meetingRepository.findById(m.getId()).get();
        assertEquals(MeetingStatus.SCHEDULED, updated.getStatus(),
                "BUG CONFIRMED: CANCELLED meeting was silently revived back to SCHEDULED!");
    }

    @Test
    @DisplayName("Bug 6: Room under MAINTENANCE can still be booked by RoomBookingService because status is ignored")
    void testRoomInMaintenanceCanStillBeBooked() {
        Room room = new Room();
        room.setRoomId("ROOM_MAINT_TEST");
        room.setRoomName("Phòng Đang Bảo Trì");
        room.setCapacity(10);
        room.setStatus("MAINTENANCE");
        roomRepository.save(room);

        OffsetDateTime start = OffsetDateTime.parse("2030-12-01T10:00:00+07:00");
        OffsetDateTime end = OffsetDateTime.parse("2030-12-01T11:00:00+07:00");
        Meeting meeting = new Meeting();
        meeting.setTitle("Họp phòng bảo trì");
        meeting.setStartTime(start);
        meeting.setEndTime(end);
        meeting.setOrganizerId(1L);
        meeting.setStatus(MeetingStatus.SCHEDULED);
        meeting.setCreatedAt(OffsetDateTime.now());
        meeting.setUpdatedAt(OffsetDateTime.now());
        meeting = meetingRepository.save(meeting);

        RoomBookingRequest bookingReq = new RoomBookingRequest();
        bookingReq.setMeetingId(meeting.getId());
        bookingReq.setRoomId(room.getRoomId());

        RoomBookingResponse resp = roomBookingService.bookRoom(bookingReq);
        assertNotNull(resp);
        assertEquals("ROOM_MAINT_TEST", resp.getRoomId(),
                "BUG CONFIRMED: Room in MAINTENANCE status was successfully booked!");
    }

    @Test
    @DisplayName("Bug 7: Equipment with status BROKEN can still be booked by EquipmentController.book")
    void testBrokenEquipmentCanStillBeBooked() {
        Equipment brokenEquipment = new Equipment();
        brokenEquipment.setEquipmentId("EQ_BROKEN_1");
        brokenEquipment.setRoomId("P.BROKEN");
        brokenEquipment.setEquipmentName("Mic hỏng");
        brokenEquipment.setType("MIC");
        brokenEquipment.setTotalQuantity(1);
        brokenEquipment.setStatus("BROKEN"); // Equipment is broken!
        equipmentRepository.save(brokenEquipment);

        OffsetDateTime start = OffsetDateTime.parse("2030-12-05T10:00:00+07:00");
        OffsetDateTime end = OffsetDateTime.parse("2030-12-05T11:00:00+07:00");

        EquipmentController.EquipmentBookingRequest bookingReq = new EquipmentController.EquipmentBookingRequest(
                "EQ_BROKEN_1", null, "P.BROKEN", 1, start, end
        );

        EquipmentController.BookingResponse resp = equipmentController.book(bookingReq);
        assertNotNull(resp);
        assertEquals("EQ_BROKEN_1", resp.equipmentId(),
                "BUG CONFIRMED: Broken equipment was successfully booked!");
    }

    @Test
    @DisplayName("Bug 8: Meeting can be booked with participants exceeding room capacity")
    void testMeetingParticipantCountExceedsRoomCapacity() {
        Room smallRoom = new Room();
        smallRoom.setRoomId("ROOM_TINY");
        smallRoom.setRoomName("Phòng nhỏ");
        smallRoom.setCapacity(2);
        smallRoom.setStatus("AVAILABLE");
        roomRepository.save(smallRoom);

        OffsetDateTime start = OffsetDateTime.parse("2030-12-10T10:00:00+07:00");
        OffsetDateTime end = OffsetDateTime.parse("2030-12-10T11:00:00+07:00");

        List<String> twentyParticipants = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            twentyParticipants.add("person" + i + "@company.com");
        }

        MeetingController.MeetingRequest req = new MeetingController.MeetingRequest(
                "Đông người trong phòng nhỏ", "Overcrowded", start, end, 1L,
                twentyParticipants, "NONE", 0, "ROOM_TINY"
        );

        Meeting created = meetingController.create(req);
        assertNotNull(created.getId(), "BUG CONFIRMED: 20 participants booked into a room with capacity of 2!");
    }
}
