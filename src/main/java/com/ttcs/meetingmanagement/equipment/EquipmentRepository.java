package com.ttcs.meetingmanagement.equipment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface EquipmentRepository
        extends JpaRepository<Equipment, String> {

    List<Equipment> findByRoomIdIgnoreCaseOrderByEquipmentNameAsc(String roomId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select equipment from Equipment equipment where equipment.equipmentId = :equipmentId")
    Optional<Equipment> findByIdForUpdate(@Param("equipmentId") String equipmentId);

    @Query(
        value = """
            SELECT COUNT(*)
            FROM meeting_equipment me JOIN meeting m ON m.meeting_id=me.meeting_id
            WHERE me.equipment_id = :equipmentId
              AND m.end_time >= CURRENT_TIMESTAMP
              AND m.status <> 'CANCELLED'
            """,
        nativeQuery = true
    )
    long countActiveBookings(
            @Param("equipmentId") String equipmentId
    );
}
