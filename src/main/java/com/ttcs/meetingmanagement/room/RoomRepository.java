package com.ttcs.meetingmanagement.room;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomRepository extends JpaRepository<Room, String> {

    boolean existsByRoomNameIgnoreCase(String roomName);

    boolean existsByRoomNameIgnoreCaseAndRoomIdNot(
            String roomName,
            String roomId
    );

    @Query(
        value = """
                SELECT COUNT(*)
                FROM meeting
                WHERE room_id = :roomId
                """,
        nativeQuery = true
    )
    long countMeetingsByRoomId(@Param("roomId") String roomId);
}