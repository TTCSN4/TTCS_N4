package com.ttcs.meetingmanagement.repository;

import com.ttcs.meetingmanagement.model.CheckInLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CheckInLogRepository extends JpaRepository<CheckInLog, String> {
    List<CheckInLog> findByMeetingId(String meetingId);
    List<CheckInLog> findByUserId(String userId);
}
