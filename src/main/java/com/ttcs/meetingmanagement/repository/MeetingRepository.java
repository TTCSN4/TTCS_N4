
package com.ttcs.meetingmanagement.repository;

import com.ttcs.meetingmanagement.model.Meeting;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface MeetingRepository
        extends JpaRepository<Meeting, String> {

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
}
