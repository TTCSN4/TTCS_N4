package com.ttcs.meetingmanagement.notification.reminder;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.springframework.data.jpa.repository.*;
import java.time.*;
import java.util.*;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.ttcs.meetingmanagement.model.Meeting;

interface US16NotificationRepository extends JpaRepository<US16Notification,String>{
  List<US16Notification> findByMeetingIdOrderByScheduledAt(String meetingId);
  Optional<US16Notification> findByMeetingIdAndUserIdAndTypeAndChannelAndScheduledAt(String meetingId,String userId,US16NotificationType type,String channel,LocalDateTime at);
  @Lock(LockModeType.PESSIMISTIC_WRITE) List<US16Notification> findByStatusInOrderByScheduledAt(Collection<US16NotificationStatus> statuses);
}
