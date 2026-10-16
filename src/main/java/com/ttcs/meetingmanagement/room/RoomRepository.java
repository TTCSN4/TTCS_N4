package com.ttcs.meetingmanagement.room;

import com.ttcs.meetingmanagement.model.Meeting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RoomRepository extends JpaRepository<Room, String> {

    // US10 - filter rooms by participant capacity
    List<Room> findByCapacityGreaterThanEqualOrderByCapacityAsc(
            Integer capacity
    );

    // US10 - filter rooms by participant capacity and status
    List<Room> findByCapacityGreaterThanEqualAndStatusIgnoreCaseOrderByCapacityAsc(
            Integer capacity,
            String status
    );

    // US10 - filter rooms by status
    List<Room> findByStatusIgnoreCaseOrderByCapacityAsc(
            String status
    );

    // US11 - check duplicate room name
    boolean existsByRoomNameIgnoreCase(String roomName);

    // US11 - check duplicate room name when updating
    boolean existsByRoomNameIgnoreCaseAndRoomIdNot(
            String roomName,
            String roomId
    );

    // US11 - count meetings using this room
    @Query("SELECT COUNT(m) FROM Meeting m WHERE m.room = :roomId")
    long countMeetingsByRoomId(
            @Param("roomId") String roomId
    );
}