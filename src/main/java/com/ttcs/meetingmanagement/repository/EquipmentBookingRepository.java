package com.ttcs.meetingmanagement.repository;

import com.ttcs.meetingmanagement.model.EquipmentBooking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EquipmentBookingRepository extends JpaRepository<EquipmentBooking, Long> {
    List<EquipmentBooking> findAllByEquipment_EquipmentId(String equipmentId);
    List<EquipmentBooking> findAllByMeetingId(Long meetingId);
}