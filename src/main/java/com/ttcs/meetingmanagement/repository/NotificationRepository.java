package com.ttcs.meetingmanagement.repository;

import com.ttcs.meetingmanagement.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, String> {
    List<Notification> findByMeetingId(String meetingId);
    List<Notification> findByUserId(String userId);
}
