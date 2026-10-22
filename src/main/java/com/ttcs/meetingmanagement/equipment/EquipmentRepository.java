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
            FROM equipment_bookings
            WHERE equipment_id = :equipmentId
              AND end_time >= CURRENT_TIMESTAMP
              AND status IN ('BOOKED', 'MAINTENANCE')
            """,
        nativeQuery = true
    )
    long countActiveBookings(
            @Param("equipmentId") String equipmentId
    );
}
