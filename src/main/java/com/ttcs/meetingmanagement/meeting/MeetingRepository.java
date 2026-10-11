package com.ttcs.meetingmanagement.meeting;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
public interface MeetingRepository extends JpaRepository<Meeting, Long> {
    List<Meeting> findDistinctByParticipants_IdInAndStartTimeLessThanAndEndTimeGreaterThan(List<Long> participantIds, LocalDateTime dayEnd, LocalDateTime dayStart);
}
