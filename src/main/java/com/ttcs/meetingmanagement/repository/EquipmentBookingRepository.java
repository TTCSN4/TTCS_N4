package com.ttcs.meetingmanagement.repository;

import com.ttcs.meetingmanagement.model.EquipmentBooking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EquipmentBookingRepository extends JpaRepository<EquipmentBooking, Long> {
<<<<<<< HEAD
    List<EquipmentBooking> findAllByEquipment_EquipmentId(String equipmentId);
    List<EquipmentBooking> findAllByMeetingId(Long meetingId);
=======
    List<EquipmentBooking> findAllByEquipment_Id(Long equipmentId);
>>>>>>> e1f3f6bac2a4ff949c9d212475e9ffdfb21b5c94
}