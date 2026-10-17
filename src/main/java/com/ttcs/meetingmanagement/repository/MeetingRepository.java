package com.ttcs.meetingmanagement.repository;

import com.ttcs.meetingmanagement.model.Meeting;
import com.ttcs.meetingmanagement.model.MeetingStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface MeetingRepository extends JpaRepository<Meeting, String> {
    @EntityGraph(attributePaths = "participantEntities")
    List<Meeting> findAllByOrderByStartTimeAsc();

    @EntityGraph(attributePaths = "participantEntities")
    List<Meeting> findAllByEndTimeBeforeOrderByStartTimeDesc(OffsetDateTime time);

    @Query("""
           SELECT COUNT(m)
           FROM Meeting m
           WHERE m.room = :roomId
             AND m.id <> :meetingId
             AND m.status <> :cancelledStatus
             AND m.startTime < :endTime
             AND m.endTime > :startTime
           """)
    long countRoomConflicts(
            @Param("roomId") String roomId,
            @Param("meetingId") String meetingId,
            @Param("cancelledStatus") MeetingStatus cancelledStatus,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );
}
