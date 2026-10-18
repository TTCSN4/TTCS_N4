package com.ttcs.meetingmanagement.repository;

import com.ttcs.meetingmanagement.model.EquipmentBooking;
import com.ttcs.meetingmanagement.model.EquipmentBookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

public interface EquipmentBookingRepository extends JpaRepository<EquipmentBooking, Long> {

    List<EquipmentBooking> findAllByEquipment_EquipmentId(String equipmentId);

    List<EquipmentBooking> findAllByMeetingId(String meetingId);

    @Query("""
        SELECT b FROM EquipmentBooking b
        WHERE b.equipment.equipmentId = :equipmentId
          AND b.status IN :statuses
          AND b.startTime < :endTime
          AND b.endTime > :startTime
    """)
    List<EquipmentBooking> findOverlappingBookings(
            @Param("equipmentId") String equipmentId,
            @Param("statuses") Collection<EquipmentBookingStatus> statuses,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );
}
