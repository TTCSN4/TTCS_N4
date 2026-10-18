package com.ttcs.meetingmanagement.room;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, String> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.roomId = :id")
    Optional<Room> findLockedById(@Param("id") String id);

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

    Optional<Room> findByRoomNameIgnoreCase(String roomName);

    @Query("""
           SELECT r FROM Room r
           WHERE UPPER(r.roomId) = UPPER(:query)
              OR UPPER(r.roomName) = UPPER(:query)
              OR UPPER(REPLACE(r.roomName, '.', '')) = UPPER(REPLACE(:query, '.', ''))
           """)
    List<Room> searchByRoomIdOrName(@Param("query") String query);

    default Optional<Room> findByRoomIdOrRoomNameFuzzy(String query) {
        if (query == null || query.isBlank()) return Optional.empty();
        List<Room> list = searchByRoomIdOrName(query.trim());
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }
}
