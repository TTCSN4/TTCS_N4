package com.ttcs.meetingmanagement.repository;

import com.ttcs.meetingmanagement.model.EquipmentBooking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EquipmentBookingRepository extends JpaRepository<EquipmentBooking, Long> {

    List<EquipmentBooking> findAllByEquipment_EquipmentId(String equipmentId);

    List<EquipmentBooking> findAllByMeetingId(String meetingId);
}
