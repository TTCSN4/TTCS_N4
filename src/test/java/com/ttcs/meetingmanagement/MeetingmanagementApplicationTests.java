package com.ttcs.meetingmanagement;

import com.ttcs.meetingmanagement.controller.EquipmentController;
import com.ttcs.meetingmanagement.model.Equipment;
import com.ttcs.meetingmanagement.model.EquipmentAvailabilityStatus;
import com.ttcs.meetingmanagement.model.EquipmentBooking;
import com.ttcs.meetingmanagement.model.EquipmentBookingStatus;
import com.ttcs.meetingmanagement.repository.EquipmentBookingRepository;
import com.ttcs.meetingmanagement.repository.EquipmentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class MeetingmanagementApplicationTests {
    @Autowired
    private EquipmentController equipmentController;

    @Autowired
    private EquipmentRepository equipmentRepository;

    @Autowired
    private EquipmentBookingRepository bookingRepository;

    @Test
    void statusAtTimeReportsAvailableBookedAndMaintenanceQuantities() {
        String room = "P-status-query";
        Equipment equipment = new Equipment();
        equipment.setName("Test projector");
        equipment.setRoom(room);
        equipment.setTotalQuantity(3);
        equipment = equipmentRepository.save(equipment);

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
        assertEquals(3, after.availableQuantity());
        assertTrue(after.statuses().contains(EquipmentAvailabilityStatus.AVAILABLE));
        assertEquals(1, after.statuses().size());
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
