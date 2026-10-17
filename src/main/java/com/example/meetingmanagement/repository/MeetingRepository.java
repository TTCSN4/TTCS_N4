package com.example.meetingmanagement.repository;

import com.example.meetingmanagement.entity.Meeting;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import java.time.OffsetDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MeetingRepository extends JpaRepository<Meeting, String> {

    List<Meeting> findAllByOrderByIdAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select meeting from Meeting meeting where meeting.id = :id")
    Optional<Meeting> findByIdForUpdate(@Param("id") String id);
    List<Meeting> findByRecurrenceSeriesIdAndStartTimeGreaterThanEqualOrderByStartTimeAsc(String seriesId,OffsetDateTime startTime);
    @Query("select count(m) from Meeting m where m.room=:room and m.id<>:id and m.status=com.example.meetingmanagement.entity.MeetingStatus.SCHEDULED and m.startTime<:endTime and m.endTime>:startTime")
    long countRoomConflicts(@Param("room") String room,@Param("id") String id,@Param("startTime") OffsetDateTime start,@Param("endTime") OffsetDateTime end);
}
