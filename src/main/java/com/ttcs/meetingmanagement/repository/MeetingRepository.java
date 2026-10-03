package com.ttcs.meetingmanagement.repository;

import com.ttcs.meetingmanagement.model.Meeting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MeetingRepository extends JpaRepository<Meeting, Long> {
    List<Meeting> findAllByOrderByStartTimeAsc();
    List<Meeting> findAllByEndTimeBeforeOrderByStartTimeDesc(java.time.OffsetDateTime time);
}