
package com.ttcs.meetingmanagement.repository;

import com.ttcs.meetingmanagement.model.EquipmentBooking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EquipmentBookingRepository
        extends JpaRepository<EquipmentBooking, com.ttcs.meetingmanagement.model.EquipmentBookingId> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select b from EquipmentBooking b join fetch b.meeting where b.equipment.equipmentId=:id")
    List<EquipmentBooking> lockForEquipment(@org.springframework.data.repository.query.Param("id") String id);

    List<EquipmentBooking> findAllByEquipment_EquipmentId(
            String equipmentId
    );

    List<EquipmentBooking> findAllByMeetingId(
            String meetingId
    );
}
