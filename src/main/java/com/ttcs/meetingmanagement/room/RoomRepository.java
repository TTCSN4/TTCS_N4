package com.ttcs.meetingmanagement.room;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

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
    @Query("""
           SELECT COUNT(m)
           FROM Meeting m
           WHERE m.roomId = :roomId
             AND m.status <> 'CANCELLED'
           """)
    long countMeetingsByRoomId(
            @Param("roomId") String roomId
    );

    java.util.Optional<Room> findByRoomNameIgnoreCase(String roomName);

    @Query("""
           SELECT r FROM Room r
           WHERE UPPER(r.roomId) = UPPER(:query)
              OR UPPER(r.roomName) = UPPER(:query)
              OR UPPER(REPLACE(r.roomName, '.', '')) = UPPER(REPLACE(:query, '.', ''))
           """)
    List<Room> searchByRoomIdOrName(@Param("query") String query);

    default java.util.Optional<Room> findByRoomIdOrRoomNameFuzzy(String query) {
        if (query == null || query.isBlank()) return java.util.Optional.empty();
        List<Room> list = searchByRoomIdOrName(query.trim());
        return list.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(list.get(0));
    }
}