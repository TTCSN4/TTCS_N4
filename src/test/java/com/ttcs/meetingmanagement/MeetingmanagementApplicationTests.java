package com.ttcs.meetingmanagement;

import com.ttcs.meetingmanagement.controller.EquipmentController;
import com.ttcs.meetingmanagement.model.Equipment;
import com.ttcs.meetingmanagement.model.Meeting;
import com.ttcs.meetingmanagement.model.MeetingStatus;
import com.ttcs.meetingmanagement.repository.EquipmentRepository;
import com.ttcs.meetingmanagement.repository.MeetingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class MeetingmanagementApplicationTests {
    @Autowired
    private EquipmentController equipmentController;

    @Autowired
    private EquipmentRepository equipmentRepository;

    @Autowired
    private MeetingRepository meetingRepository;

    @Test
    void overlappingBookingsCannotExceedEquipmentQuantity() {
        Equipment equipment = createEquipment("P-booking-capacity", 2);
        OffsetDateTime start = OffsetDateTime.parse("2030-05-10T09:00:00+07:00");
        OffsetDateTime end = OffsetDateTime.parse("2030-05-10T11:00:00+07:00");

        equipmentController.book(request(equipment, null, "P-booking-capacity", 1, start, end));
        equipmentController.book(request(
                equipment, null, "P-booking-capacity", 1,
                start.plusHours(1), end.plusHours(1)
        ));

        ResponseStatusException conflict = assertThrows(ResponseStatusException.class, () ->
                equipmentController.book(request(
                        equipment, null, "P-booking-capacity", 1,
                        start.plusMinutes(90), start.plusMinutes(100)
                )));
        assertEquals(409, conflict.getStatusCode().value());

        equipmentController.book(request(
                equipment, null, "P-booking-capacity", 2,
                end.plusHours(1), end.plusHours(2)
        ));
    }

    @Test
    void bookingMustMatchMeetingRoomAndTime() {
        String room = "P-booking-meeting";
        Equipment equipment = createEquipment(room, 1);
        OffsetDateTime start = OffsetDateTime.parse("2030-05-11T09:00:00+07:00");
        Meeting meeting = new Meeting();
        meeting.setTitle("Test meeting");
        meeting.setRoom(room);
        meeting.setStartTime(start);
        meeting.setEndTime(start.plusHours(1));
        meeting.setOrganizerId(1L);
        meeting.setStatus(MeetingStatus.SCHEDULED);
        meeting.setCreatedAt(start.minusDays(1));
        meeting.setUpdatedAt(start.minusDays(1));
        Meeting savedMeeting = meetingRepository.save(meeting);

        ResponseStatusException roomMismatch = assertThrows(ResponseStatusException.class, () ->
                equipmentController.book(request(
                        equipment, savedMeeting.getId(), "P-other-room", 1, start, start.plusMinutes(30)
                )));
        assertEquals(400, roomMismatch.getStatusCode().value());

        ResponseStatusException outsideMeeting = assertThrows(ResponseStatusException.class, () ->
                equipmentController.book(request(
                        equipment, savedMeeting.getId(), room, 1, start.minusMinutes(15), start.plusMinutes(30)
                )));
        assertEquals(400, outsideMeeting.getStatusCode().value());
    }

    private Equipment createEquipment(String room, int quantity) {
        Equipment equipment = new Equipment();
        equipment.setName("Test projector");
        equipment.setRoom(room);
        equipment.setTotalQuantity(quantity);
        return equipmentRepository.save(equipment);
    }

    private EquipmentController.EquipmentBookingRequest request(
            Equipment equipment,
            Long meetingId,
            String room,
            int quantity,
            OffsetDateTime start,
            OffsetDateTime end
    ) {
        return new EquipmentController.EquipmentBookingRequest(
                equipment.getId(), meetingId, room, quantity, start, end
        );
    }
}
