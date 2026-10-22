package com.ttcs.meetingmanagement.equipment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface EquipmentRepository extends JpaRepository<Equipment, String> {

    boolean existsByRoomId(String roomId);

    List<Equipment> findByRoomIdIgnoreCaseOrderByEquipmentNameAsc(String roomId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select equipment from Equipment equipment where equipment.equipmentId = :equipmentId")
    Optional<Equipment> findByIdForUpdate(@Param("equipmentId") String equipmentId);

    @Query(
        value = """
            SELECT (
                (SELECT COALESCE(COUNT(*), 0)
                 FROM meeting_equipment me
                 INNER JOIN `meeting` m ON m.meeting_id = me.meeting_id
                 WHERE me.equipment_id = :equipmentId
                   AND m.end_time >= NOW()
                   AND m.status NOT IN ('CANCELLED', 'CANCELED', 'COMPLETED', 'FINISHED', 'ENDED'))
                +
                (SELECT COALESCE(COUNT(*), 0)
                 FROM equipment_bookings eb
                 WHERE eb.equipment_id = :equipmentId
                   AND eb.end_time >= NOW()
                   AND eb.status IN ('BOOKED', 'MAINTENANCE')
                   AND (eb.meeting_id IS NULL OR eb.meeting_id NOT IN (
                       SELECT me2.meeting_id FROM meeting_equipment me2 WHERE me2.equipment_id = :equipmentId
                   )))
            )
            """,
        nativeQuery = true
    )
    long countActiveBookings(@Param("equipmentId") String equipmentId);

    @Query(
        value = """
            SELECT GREATEST(
                (SELECT COALESCE(MAX(me.quantity), 0)
                 FROM meeting_equipment me
                 INNER JOIN `meeting` m ON m.meeting_id = me.meeting_id
                 WHERE me.equipment_id = :equipmentId
                   AND m.end_time >= NOW()
                   AND m.status NOT IN ('CANCELLED', 'CANCELED', 'COMPLETED', 'FINISHED', 'ENDED')),
                (SELECT COALESCE(MAX(eb.quantity), 0)
                 FROM equipment_bookings eb
                 WHERE eb.equipment_id = :equipmentId
                   AND eb.end_time >= NOW()
                   AND eb.status IN ('BOOKED', 'MAINTENANCE'))
            )
            """,
        nativeQuery = true
    )
    int maxBookedQuantity(@Param("equipmentId") String equipmentId);

    @Query(
        value = """
            SELECT (
                (SELECT COALESCE(SUM(me.quantity), 0)
                 FROM meeting_equipment me
                 INNER JOIN `meeting` m ON m.meeting_id = me.meeting_id
                 WHERE me.equipment_id = :equipmentId
                   AND m.start_time <= NOW() AND m.end_time >= NOW()
                   AND m.status NOT IN ('CANCELLED', 'CANCELED', 'COMPLETED', 'FINISHED', 'ENDED'))
                +
                (SELECT COALESCE(SUM(eb.quantity), 0)
                 FROM equipment_bookings eb
                 WHERE eb.equipment_id = :equipmentId
                   AND eb.start_time <= NOW() AND eb.end_time >= NOW()
                   AND eb.status IN ('BOOKED', 'MAINTENANCE')
                   AND (eb.meeting_id IS NULL OR eb.meeting_id NOT IN (
                       SELECT me2.meeting_id FROM meeting_equipment me2 WHERE me2.equipment_id = :equipmentId
                   )))
            )
            """,
        nativeQuery = true
    )
    int countCurrentReservedQuantity(@Param("equipmentId") String equipmentId);
}
