package com.ttcs.meetingmanagement.chatbot;
import com.ttcs.meetingmanagement.room.Room;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
interface US25ChatRoomRepository extends JpaRepository<Room,String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.status in ('ACTIVE','AVAILABLE') and r.capacity >= :capacity order by r.capacity")
    List<Room> findAvailableForBooking(@Param("capacity") int capacity);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.roomId = :id")
    Optional<Room> findLockedById(@Param("id") String id);
}
