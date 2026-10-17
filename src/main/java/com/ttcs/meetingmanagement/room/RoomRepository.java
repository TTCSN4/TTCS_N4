package com.ttcs.meetingmanagement.room;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoomRepository extends JpaRepository<Room, String> {

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

    // US11 - count active meetings using this room
    @Query(
        value = """
                SELECT COUNT(*)
                FROM meeting
                WHERE room_id = :roomId
                  AND status <> 'CANCELLED'
                """,
        nativeQuery = true
    )
    long countMeetingsByRoomId(
            @Param("roomId") String roomId
    );
}