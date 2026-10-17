package com.ttcs.meetingmanagement;

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

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:us13test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class MeetingmanagementApplicationTests {
    @Autowired
    private EquipmentController equipmentController;

    @Autowired
    private EquipmentRepository equipmentRepository;

    @Autowired
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
        meeting.setId("M-participants");
        meeting.setTitle("Meeting with participants");
        meeting.setStartTime(start);
        meeting.setEndTime(start.plusHours(1));
        meeting.setOrganizerId("U1");
        meeting.setStatus(MeetingStatus.SCHEDULED);
        meeting.setCreatedAt(start.minusDays(1));
        meeting.setUpdatedAt(start.minusDays(1));
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

        EquipmentController.EquipmentStatusResponse during = equipmentController
                .status(room, start.plusMinutes(30))
                .get(0);
        assertEquals(3, during.totalQuantity());
        assertEquals(1, during.bookedQuantity());
        assertEquals(0, during.maintenanceQuantity());
        assertEquals(2, during.availableQuantity());
        assertEquals(List.of(
                EquipmentAvailabilityStatus.AVAILABLE,
                EquipmentAvailabilityStatus.BOOKED
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
        Meeting bookedMeeting=saveReservation(equipment, room, 1, start, start.plusHours(1), EquipmentBookingStatus.BOOKED);

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () ->
                equipmentController.book(new EquipmentController.EquipmentBookingRequest(
                        equipment.getEquipmentId(),
                        bookedMeeting.getId(),
                        room,
                        1,
                        start.plusMinutes(15),
                        start.plusMinutes(45)
                ))
        );
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
    }

    @Test
    @Transactional
    void statusQueryHonorsAdministrativeMaintenanceState() {
        String room = "P-admin-maintenance";
        Equipment equipment = saveEquipment(room, "Broken projector", 2);
        equipment.setStatus("BROKEN");
        equipmentRepository.saveAndFlush(equipment);

        EquipmentController.EquipmentStatusResponse status = equipmentController
                .status(room, OffsetDateTime.parse("2030-05-10T09:30:00+07:00"))
                .get(0);

        assertEquals(0, status.availableQuantity());
        assertEquals(2, status.maintenanceQuantity());
        assertEquals(List.of(EquipmentAvailabilityStatus.MAINTENANCE), status.statuses());
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

    private Meeting saveReservation(
            Equipment equipment,
            String room,
            int quantity,
            OffsetDateTime start,
            OffsetDateTime end,
            EquipmentBookingStatus status
    ) {
        EquipmentBooking reservation = new EquipmentBooking();
        Meeting meeting=new Meeting();meeting.setId("M-"+java.util.UUID.randomUUID());meeting.setTitle("Test");meeting.setRoom(room);meeting.setStartTime(start);meeting.setEndTime(end);meeting.setOrganizerId("U1");meeting.setStatus(MeetingStatus.SCHEDULED);meeting.setCreatedAt(start.minusDays(1));meeting.setUpdatedAt(start.minusDays(1));meetingRepository.save(meeting);
        reservation.setEquipment(equipment);
        reservation.setMeeting(meeting);
        reservation.setQuantity(quantity);
        bookingRepository.save(reservation);
        return meeting;
    }
}
