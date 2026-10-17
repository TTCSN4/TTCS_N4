package com.ttcs.meetingmanagement.repository;

import com.ttcs.meetingmanagement.model.EquipmentBooking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.time.OffsetDateTime;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EquipmentBookingRepository extends JpaRepository<EquipmentBooking, Long> {
    List<EquipmentBooking> findAllByEquipment_EquipmentId(String equipmentId);
    List<EquipmentBooking> findAllByMeetingId(Long meetingId);
    @Query("select coalesce(sum(b.quantity),0) from EquipmentBooking b where b.equipment.equipmentId=:equipmentId and b.status<>com.ttcs.meetingmanagement.model.EquipmentBookingStatus.CANCELLED and b.startTime<:endTime and b.endTime>:startTime")
    long reservedQuantity(@Param("equipmentId") String equipmentId,@Param("startTime") OffsetDateTime startTime,@Param("endTime") OffsetDateTime endTime);
}
