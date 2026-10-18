package com.ttcs.meetingmanagement.repository;

import com.ttcs.meetingmanagement.model.Meeting;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MeetingRepository
        extends JpaRepository<Meeting, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Meeting m where m.meetingId = :id")
    Optional<Meeting> findByIdForUpdate(@Param("id") String id);

    @Query(value = "select count(*) from `user` where user_id = :id", nativeQuery = true)
    long countUserById(@Param("id") String id);

    @Query(value = "select count(*) from room where room_id = :id", nativeQuery = true)
    long countRoomById(@Param("id") String id);

    default long countRoomConflicts(String room, LocalDateTime start, LocalDateTime end) {
        return countRoomConflicts(room, null, start, end);
    }

    default long countRoomConflicts(String room, String meeting, String ignoredStatus, LocalDateTime start, LocalDateTime end) {
        return countRoomConflicts(room, meeting, start, end);
    }

    List<Meeting> findByStatusIn(Collection<String> statuses);

    long countByRoomIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(
            String room, Collection<String> statuses, LocalDateTime end, LocalDateTime start);

    // Lay tat ca cuoc hop theo thoi gian bat dau
    List<Meeting> findAllByOrderByStartTimeAsc();

    // Lay cac cuoc hop da ket thuc
    List<Meeting> findAllByEndTimeBeforeOrderByStartTimeDesc(
            LocalDateTime time
    );

    // Kiem tra phong co bi trung lich hay khong
    @Query("""
        SELECT COUNT(m)
        FROM Meeting m
        WHERE m.roomId = :roomId
          AND (:meetingId IS NULL
               OR m.meetingId <> :meetingId)
          AND UPPER(TRIM(m.status)) <> 'CANCELLED'
          AND m.startTime < :endTime
          AND m.endTime > :startTime
        """)
    long countRoomConflicts(
            @Param("roomId") String roomId,
            @Param("meetingId") String meetingId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );
}