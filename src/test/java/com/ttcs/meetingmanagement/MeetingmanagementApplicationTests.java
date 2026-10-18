
package com.ttcs.meetingmanagement;

import com.ttcs.meetingmanagement.equipment.Equipment;
import com.ttcs.meetingmanagement.equipment.EquipmentController;
import com.ttcs.meetingmanagement.equipment.EquipmentRepository;

import com.ttcs.meetingmanagement.model.EquipmentAvailabilityStatus;
import com.ttcs.meetingmanagement.model.EquipmentBooking;
import com.ttcs.meetingmanagement.model.EquipmentBookingStatus;
import com.ttcs.meetingmanagement.model.Meeting;

import com.ttcs.meetingmanagement.repository.EquipmentBookingRepository;
import com.ttcs.meetingmanagement.repository.MeetingRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:meeting_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class MeetingmanagementApplicationTests {

    @Autowired private com.ttcs.meetingmanagement.equipment.EquipmentReservationService reservations;
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

    // ==========================================
    // TEST 1: KIEM TRA LUU VA DOC CUOC HOP
    // THEO ERD MOI
    // ==========================================

    @Test
    @Transactional
    void meetingQueriesWorkWithStringIdAndLocalDateTime() {

        LocalDateTime start =
                LocalDateTime.of(2030, 7, 10, 9, 0);

        Meeting meeting = new Meeting();

        meeting.setMeetingId(
                UUID.randomUUID().toString()
        );

        meeting.setTitle("Meeting test");
        meeting.setDescription("Test meeting repository");

        meeting.setStartTime(start);
        meeting.setEndTime(start.plusHours(1));

        meeting.setOrganizerId("USER_TEST");

        meeting.setStatus("SCHEDULED");

        meeting.setIsRecurring(false);

        meeting.setCreatedAt(start.minusDays(1));
        meeting.setUpdatedAt(start.minusDays(1));

        // Luu vao database test
        Meeting saved =
                meetingRepository.saveAndFlush(meeting);

        // Xoa cache JPA de kiem tra doc lai tu DB
        entityManager.clear();

        Meeting loaded =
                meetingRepository
                        .findAllByOrderByStartTimeAsc()
                        .stream()
                        .filter(item ->
                                item.getMeetingId()
                                        .equals(saved.getMeetingId())
                        )
                        .findFirst()
                        .orElseThrow();

        assertEquals(
                saved.getMeetingId(),
                loaded.getMeetingId()
        );

        assertEquals(
                "Meeting test",
                loaded.getTitle()
        );

        assertEquals(
                start,
                loaded.getStartTime()
        );

        assertEquals(
                start.plusHours(1),
                loaded.getEndTime()
        );

        assertEquals(
                "SCHEDULED",
                loaded.getStatus()
        );
    }

    // ==========================================
    // TEST 2: KIEM TRA TRANG THAI THIET BI
    // ==========================================

    @Test
    @Transactional
    void statusAtTimeReportsAvailableBookedAndMaintenanceQuantities() {

        String room = "P-status-query";

        Equipment equipment =
                saveEquipment(room, "Test projector", 3);

        OffsetDateTime start =
                OffsetDateTime.parse(
                        "2030-05-10T09:00:00+07:00"
                );

        saveReservation(
                equipment,
                room,
                1,
                start,
                start.plusHours(1),
                EquipmentBookingStatus.BOOKED
        );

        EquipmentController.EquipmentStatusResponse during =
                equipmentController
                        .status(room, start.plusMinutes(30))
                        .get(0);

        assertEquals(3, during.totalQuantity());
        assertEquals(1, during.bookedQuantity());
        assertEquals(0, during.maintenanceQuantity());
        assertEquals(2, during.availableQuantity());

        EquipmentController.EquipmentStatusResponse after =
                equipmentController
                        .status(room, start.plusHours(1))
                        .get(0);

        assertEquals(3, after.totalQuantity());
        assertEquals(3, after.availableQuantity());

        assertTrue(
                after.statuses().contains(
                        EquipmentAvailabilityStatus.AVAILABLE
                )
        );

        assertEquals(1, after.statuses().size());
    }

    // ==========================================
    // TEST 3: KIEM TRA DAT THIET BI TRUNG LICH
    // ==========================================

    @Test
    @Transactional
    void bookingRejectsOverlappingRequestsBeyondInventory() {

        String room = "P-booking-conflict";

        Equipment equipment =
                saveEquipment(room, "One projector", 1);

        OffsetDateTime start =
                OffsetDateTime.parse(
                        "2030-06-10T09:00:00+07:00"
                );

        saveReservation(
                equipment,
                room,
                1,
                start,
                start.plusHours(1),
                EquipmentBookingStatus.BOOKED
        );

        ResponseStatusException error =
                assertThrows(
                        ResponseStatusException.class,
                        () -> {
                            Meeting m=new Meeting();m.meetingId="M-new";m.organizerId="U1";m.roomId=room;m.title="New";m.status="SCHEDULED";
                            m.startTime=start.toLocalDateTime().plusMinutes(15);m.endTime=m.startTime.plusMinutes(30);meetingRepository.saveAndFlush(m);
                            reservations.replace(m,List.of(new com.ttcs.meetingmanagement.equipment.EquipmentReservationService.Item(equipment.getEquipmentId(),1)));
                        }
                );

        assertEquals(
                HttpStatus.CONFLICT,
                error.getStatusCode()
        );
    }

    // ==========================================
    // HAM HO TRO TAO THIET BI TEST
    // ==========================================

    private Equipment saveEquipment(
            String room,
            String name,
            int quantity) {

        Equipment equipment = new Equipment();

        equipment.setEquipmentId(
                "EQ-" + UUID.randomUUID()
        );

        equipment.setRoomId(room);
        equipment.setEquipmentName(name);
        equipment.setType("Test");
        equipment.setTotalQuantity(quantity);
        equipment.setStatus("AVAILABLE");

        return equipmentRepository.saveAndFlush(
                equipment
        );
    }

    // ==========================================
    // HAM HO TRO TAO LICH DAT THIET BI
    // ==========================================

    private void saveReservation(
            Equipment equipment,
            String room,
            int quantity,
            OffsetDateTime start,
            OffsetDateTime end,
            EquipmentBookingStatus status) {

        EquipmentBooking reservation =
                new EquipmentBooking();

        reservation.setEquipment(equipment);
        Meeting m=new Meeting();m.organizerId="U1";m.roomId=room;m.title="Reservation";m.startTime=start.toLocalDateTime();m.endTime=end.toLocalDateTime();m.status="SCHEDULED";
        meetingRepository.saveAndFlush(m);reservation.setMeeting(m);
        reservation.setQuantity(quantity);




        bookingRepository.save(reservation);
    }
}
