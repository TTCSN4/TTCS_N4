package com.ttcs.meetingmanagement.repository;

import com.ttcs.meetingmanagement.model.Meeting;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MeetingRepository extends JpaRepository<Meeting, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Meeting m WHERE m.meetingId = :id")
    Optional<Meeting> findByIdForUpdate(@Param("id") String id);

    @Query(value = "SELECT COUNT(*) FROM `user` WHERE user_id = :id", nativeQuery = true)
    long countUserById(@Param("id") String id);

    @Query(value = "SELECT COUNT(*) FROM room WHERE room_id = :id", nativeQuery = true)
    long countRoomById(@Param("id") String id);

    default long countRoomConflicts(String roomId, LocalDateTime startTime, LocalDateTime endTime) {
        return countRoomConflicts(roomId, null, startTime, endTime);
    }

    default long countRoomConflicts(String room, String meeting, String ignoredStatus, LocalDateTime start, LocalDateTime end) {
        return countRoomConflicts(room, meeting, start, end);
    }

    List<Meeting> findByStatusIn(Collection<String> statuses);

    long countByRoomIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(
            String room, Collection<String> statuses, LocalDateTime end, LocalDateTime start);

    // Kiem tra phong co bi trung lich hay khong
    @Query("""
        SELECT COUNT(m)
        FROM Meeting m
        WHERE m.roomId = :roomId
          AND (:meetingId IS NULL OR m.meetingId <> :meetingId)
          AND m.status <> 'CANCELLED'
          AND m.startTime < :endTime
          AND m.endTime > :startTime
        """)
    long countRoomConflicts(
            @Param("roomId") String roomId,
            @Param("meetingId") String meetingId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    List<Meeting> findAllByOrderByStartTimeAsc();

    List<Meeting> findAllByEndTimeBeforeOrderByStartTimeDesc(LocalDateTime time);
}
