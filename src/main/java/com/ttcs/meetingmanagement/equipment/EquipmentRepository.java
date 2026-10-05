package com.ttcs.meetingmanagement.equipment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface EquipmentRepository extends JpaRepository<Equipment, String> {

    // Tìm tất cả thiết bị thuộc về một phòng họp cụ thể
    List<Equipment> findByRoomId(String roomId);

    @Query(
        value = """
            SELECT COUNT(*)
            FROM meeting_equipment me
            INNER JOIN `meeting` m
                ON m.meeting_id = me.meeting_id
            WHERE me.equipment_id = :equipmentId
              AND m.end_time >= NOW()
              AND m.status NOT IN ('CANCELLED', 'CANCELED')
            """,
        nativeQuery = true
    )
    long countActiveBookings(
            @Param("equipmentId") String equipmentId
    );
}