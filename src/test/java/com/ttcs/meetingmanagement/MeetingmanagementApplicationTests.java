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
import com.ttcs.meetingmanagement.controller.MeetingController;
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
        "spring.datasource.url=jdbc:h2:mem:us12test;MODE=MySQL;DB_CLOSE_DELAY=-1",
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

    @Autowired
    private MeetingController meetingController;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @Transactional
    void meetingQueriesFetchParticipantsBeforeSerialization() {
        OffsetDateTime start = OffsetDateTime.parse("2030-07-10T09:00:00+07:00");
        Meeting meeting = new Meeting();
        meeting.setTitle("Meeting with participants");
        meeting.setStartTime(start);
        meeting.setEndTime(start.plusHours(1));
        meeting.setOrganizerId(1L);
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

    @Test
    void creatingMeetingAndEquipmentIsAtomic() {
        long meetingsBefore = meetingRepository.count();
        OffsetDateTime start = OffsetDateTime.parse("2030-08-10T09:00:00.123456789+07:00");

        assertThrows(ResponseStatusException.class, () -> meetingController.create(
                new MeetingController.MeetingRequest(
                        "Atomic meeting", "", start, start.plusHours(1), 1L,
                        List.of(), "WEEKLY", 1, "P-atomic", List.of("missing-equipment")
                )
        ));

        assertEquals(meetingsBefore, meetingRepository.count());
    }

    @Test
    @Transactional
    void recurringMeetingsPreserveExactDuration() {
        OffsetDateTime start = OffsetDateTime.parse("2030-08-10T09:00:00.123456789+07:00");
        Meeting first = meetingController.create(new MeetingController.MeetingRequest(
                "Precise recurrence", "", start, start.plusMinutes(45).plusNanos(321), 1L,
                List.of(), "MONTHLY", 1, "P-precision", List.of()
        ));

        Meeting repeated = meetingRepository.findAll().stream()
                .filter(item -> first.getId().equals(item.getRecurrenceSeriesId()))
                .findFirst()
                .orElseThrow();
        assertEquals(
                java.time.Duration.between(first.getStartTime(), first.getEndTime()),
                java.time.Duration.between(repeated.getStartTime(), repeated.getEndTime())
        );
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
    }
}
