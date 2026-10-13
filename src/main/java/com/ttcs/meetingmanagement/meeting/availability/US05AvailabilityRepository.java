package com.ttcs.meetingmanagement.meeting.availability;

import com.ttcs.meetingmanagement.model.Meeting;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface US05AvailabilityRepository extends Repository<Meeting, String> {
    @Query("""
        select m from Meeting m where m.status not in ('CANCELLED', 'CANCELED')
        and m.startTime < :end and m.endTime > :start
        and (m.organizerId in :users or m.meetingId in (
            select p.meetingId from MeetingParticipant p where p.userId in :users
            and p.status not in ('DECLINED', 'REMOVED')))
        """)
    List<Meeting> busy(@Param("users") List<String> users, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
