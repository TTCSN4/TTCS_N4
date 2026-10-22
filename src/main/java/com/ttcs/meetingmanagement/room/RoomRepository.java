package com.ttcs.meetingmanagement.room;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RoomRepository extends JpaRepository<Room, String> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.roomId = :id")
    java.util.Optional<Room> findLockedById(@Param("id") String id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    List<Room> findByStatusAndCapacityGreaterThanEqualOrderByCapacity(String status, int capacity);

    // US10 - filter rooms by participant capacity
    List<Room> findByCapacityGreaterThanEqualOrderByCapacityAsc(
            Integer capacity
    );

    // US11 - check duplicate room name
    boolean existsByRoomNameIgnoreCase(String roomName);

    // US11 - check duplicate room name when updating
    boolean existsByRoomNameIgnoreCaseAndRoomIdNot(
            String roomName,
            String roomId
    );

    // US11 - count meetings using this room
    @Query(
        value = """
                SELECT COUNT(*)
                FROM meeting
                WHERE room_id = :roomId
                """,
        nativeQuery = true
    )
    long countMeetingsByRoomId(
            @Param("roomId") String roomId
    );
}
