package com.ttcs.meetingmanagement.repository;

import com.ttcs.meetingmanagement.model.CalendarSync;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CalendarSyncRepository extends JpaRepository<CalendarSync, String> {
    List<CalendarSync> findByMeetingId(String meetingId);
    List<CalendarSync> findByUserId(String userId);
}
