package com.ttcs.meetingmanagement;

import com.ttcs.meetingmanagement.equipment.Equipment;
import com.ttcs.meetingmanagement.equipment.EquipmentRepository;
import com.ttcs.meetingmanagement.equipment.MeetingEquipment;
import com.ttcs.meetingmanagement.equipment.MeetingEquipmentId;
import com.ttcs.meetingmanagement.equipment.MeetingEquipmentRepository;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class MeetingmanagementApplicationTests {
    @Autowired
    private EquipmentRepository equipmentRepository;

    @Autowired
    private MeetingEquipmentRepository meetingEquipmentRepository;

    @Test
    @Transactional
    void meetingEquipmentQuantityIsMappedAndPersistedByJpa() {
        Equipment equipment = new Equipment();
        equipment.setEquipmentId("EQ-JPA-TEST");
        equipment.setEquipmentName("Projector");
        equipment.setType("DISPLAY");
        equipment.setTotalQuantity(3);
        equipment.setStatus("AVAILABLE");
        equipment = equipmentRepository.saveAndFlush(equipment);

        MeetingEquipment reservation = new MeetingEquipment();
        reservation.setId(new MeetingEquipmentId("MEETING-JPA-TEST", equipment.getEquipmentId()));
        reservation.setEquipment(equipment);
        reservation.setQuantity(2);
        meetingEquipmentRepository.saveAndFlush(reservation);

        MeetingEquipment persisted = meetingEquipmentRepository.findById(reservation.getId()).orElseThrow();
        assertEquals(2, persisted.getQuantity());
        assertEquals(equipment.getEquipmentId(), persisted.getEquipment().getEquipmentId());
    }
}