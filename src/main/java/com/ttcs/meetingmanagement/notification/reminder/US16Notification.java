package com.ttcs.meetingmanagement.notification.reminder;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.springframework.data.jpa.repository.*;
import java.time.*;
import java.util.*;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.ttcs.meetingmanagement.model.Meeting;

@JsonAutoDetect(fieldVisibility=JsonAutoDetect.Visibility.ANY)
@Entity @Table(name="notification",uniqueConstraints=@UniqueConstraint(name="uk_notification_meeting_user_type_time",columnNames={"meeting_id","user_id","type","channel","scheduled_at"})) class US16Notification {
  @Id @Column(name="notification_id",length=36) String id; @Column(name="meeting_id",nullable=false,length=36) String meetingId;
  @Column(name="user_id",nullable=false,length=36) String userId; @Column(name="recipient_email") String recipientEmail;
  @Column(name="lead_minutes",nullable=false) int leadMinutes=15;
  @Enumerated(EnumType.STRING) @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
  @Column(nullable=false,length=50) US16NotificationType type;
  @Column(nullable=false,length=16) String channel;
  @Column(name="scheduled_at",nullable=false) LocalDateTime scheduledAt; @Column(name="sent_at") LocalDateTime sentAt;
  @Column(name="last_attempt_at") LocalDateTime lastAttemptAt; @Column(name="next_attempt_at") LocalDateTime nextAttemptAt;
  @Column(name="retry_count",nullable=false) int retryCount; @Column(name="error_message",length=1000) String errorMessage;
  @Enumerated(EnumType.STRING) @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
  @Column(nullable=false,length=50) US16NotificationStatus status=US16NotificationStatus.PENDING;
  @PrePersist void create(){if(id==null)id=UUID.randomUUID().toString();}
}
