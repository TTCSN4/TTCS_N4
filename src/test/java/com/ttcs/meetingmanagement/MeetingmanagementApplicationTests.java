package com.ttcs.meetingmanagement;

<<<<<<< HEAD
import com.ttcs.meetingmanagement.equipment.Equipment;
import com.ttcs.meetingmanagement.equipment.EquipmentController;
import com.ttcs.meetingmanagement.equipment.EquipmentRepository;
import com.ttcs.meetingmanagement.model.EquipmentAvailabilityStatus;
import com.ttcs.meetingmanagement.model.EquipmentBooking;
import com.ttcs.meetingmanagement.model.EquipmentBookingStatus;
import com.ttcs.meetingmanagement.repository.EquipmentBookingRepository;
import com.ttcs.meetingmanagement.model.Meeting;
import com.ttcs.meetingmanagement.model.MeetingStatus;
import com.ttcs.meetingmanagement.repository.MeetingRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
=======
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
>>>>>>> e1f3f6bac2a4ff949c9d212475e9ffdfb21b5c94

@SpringBootTest
class MeetingmanagementApplicationTests {
    @Autowired
    private EquipmentController equipmentController;

    @Autowired
    private EquipmentRepository equipmentRepository;

    @Autowired
<<<<<<< HEAD
    private EquipmentBookingRepository bookingRepository;

    @Autowired
    private MeetingRepository meetingRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @Transactional
    void meetingQueriesFetchParticipantsBeforeSerialization() {
        OffsetDateTime start = OffsetDateTime.parse("2030-07-10T09:00:00+07:00");
        Meeting meeting = new Meeting();
        meeting.setTitle("Meeting with participants");
=======
    private MeetingRepository meetingRepository;

    @Test
    void overlappingBookingsCannotExceedEquipmentQuantity() {
        Equipment equipment = createEquipment("P-booking-capacity", 2);
        equipmentRepository.flush();
        Equipment savedEquipment = equipmentRepository.findById(equipment.getId()).orElseThrow();
        assertEquals(2, savedEquipment.getQuantity());

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
>>>>>>> e1f3f6bac2a4ff949c9d212475e9ffdfb21b5c94
        meeting.setStartTime(start);
        meeting.setEndTime(start.plusHours(1));
        meeting.setOrganizerId(1L);
        meeting.setStatus(MeetingStatus.SCHEDULED);
        meeting.setCreatedAt(start.minusDays(1));
        meeting.setUpdatedAt(start.minusDays(1));
<<<<<<< HEAD
        meeting.setParticipants(List.of("attendee@example.com"));
        Meeting saved = meetingRepository.saveAndFlush(meeting);
        entityManager.clear();

        Meeting loaded = meetingRepository.findAllByOrderByStartTimeAsc().stream()
                .filter(item -> item.getId().equals(saved.getId()))
                .findFirst()
                .orElseThrow();
        assertTrue(entityManager.getEntityManagerFactory()
                .getPersistenceUnitUtil()
                .isLoaded(loaded, "participants"));
        assertEquals(List.of("attendee@example.com"), loaded.getParticipants());
    }

    @Test
    @Transactional
    void statusAtTimeReportsAvailableBookedAndMaintenanceQuantities() {
        String room = "P-status-query";
        Equipment equipment = saveEquipment(room, "Test projector", 3);

        OffsetDateTime start = OffsetDateTime.parse("2030-05-10T09:00:00+07:00");
        saveReservation(equipment, room, 1, start, start.plusHours(1), EquipmentBookingStatus.BOOKED);
        saveReservation(equipment, room, 1, start, start.plusHours(1), EquipmentBookingStatus.MAINTENANCE);

        EquipmentController.EquipmentStatusResponse during = equipmentController
                .status(room, start.plusMinutes(30))
                .get(0);
        assertEquals(3, during.totalQuantity());
        assertEquals(1, during.bookedQuantity());
        assertEquals(1, during.maintenanceQuantity());
        assertEquals(1, during.availableQuantity());
        assertEquals(List.of(
                EquipmentAvailabilityStatus.AVAILABLE,
                EquipmentAvailabilityStatus.BOOKED,
                EquipmentAvailabilityStatus.MAINTENANCE
        ), during.statuses());

        EquipmentController.EquipmentStatusResponse after = equipmentController
                .status(room, start.plusHours(1))
                .get(0);
        assertEquals(3, after.totalQuantity());
        assertEquals(3, after.availableQuantity());
        assertTrue(after.statuses().contains(EquipmentAvailabilityStatus.AVAILABLE));
        assertEquals(1, after.statuses().size());
    }

    @Test
    @Transactional
    void bookingRejectsOverlappingRequestsBeyondInventory() {
        String room = "P-booking-conflict";
        Equipment equipment = saveEquipment(room, "One projector", 1);
        OffsetDateTime start = OffsetDateTime.parse("2030-06-10T09:00:00+07:00");
        saveReservation(equipment, room, 1, start, start.plusHours(1), EquipmentBookingStatus.BOOKED);

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () ->
                equipmentController.book(new EquipmentController.EquipmentBookingRequest(
                        equipment.getEquipmentId(),
                        null,
                        room,
                        1,
                        start.plusMinutes(15),
                        start.plusMinutes(45)
                ))
        );
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
    }

    private Equipment saveEquipment(String room, String name, int quantity) {
        Equipment equipment = new Equipment();
        equipment.setEquipmentId("EQ-" + java.util.UUID.randomUUID());
        equipment.setRoomId(room);
        equipment.setEquipmentName(name);
        equipment.setType("Test");
        equipment.setTotalQuantity(quantity);
        equipment.setStatus("AVAILABLE");
        return equipmentRepository.saveAndFlush(equipment);
    }

    private void saveReservation(
            Equipment equipment,
            String room,
            int quantity,
            OffsetDateTime start,
            OffsetDateTime end,
            EquipmentBookingStatus status
    ) {
        EquipmentBooking reservation = new EquipmentBooking();
        reservation.setEquipment(equipment);
        reservation.setRoom(room);
        reservation.setQuantity(quantity);
        reservation.setStartTime(start);
        reservation.setEndTime(end);
        reservation.setStatus(status);
        bookingRepository.save(reservation);
=======
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
        equipment.setQuantity(quantity);
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
>>>>>>> e1f3f6bac2a4ff949c9d212475e9ffdfb21b5c94
    }
}
