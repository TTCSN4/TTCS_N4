package com.ttcs.meetingmanagement.notification.reminder;
import com.ttcs.meetingmanagement.model.*;
import com.ttcs.meetingmanagement.repository.*;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.ttcs.meetingmanagement.notification.reminder.US16NotificationStatus.*;
import static com.ttcs.meetingmanagement.notification.reminder.US16NotificationType.*;
class US16ReminderServiceTest {
    MeetingRepository meetings=mock(MeetingRepository.class);MeetingParticipantRepository participants=mock(MeetingParticipantRepository.class);
    UserRepository users=mock(UserRepository.class);US16NotificationRepository notifications=mock(US16NotificationRepository.class);
    US16EmailGateway email=mock(US16EmailGateway.class);
    Clock clock=Clock.fixed(Instant.parse("2099-01-01T02:00:00Z"),ZoneId.of("Asia/Ho_Chi_Minh"));
    US16ReminderService service=new US16ReminderService(meetings,participants,users,notifications,email,clock,3);
    Meeting meeting;US16Notification notification;
    @BeforeEach void fixture(){
        meeting=new Meeting();meeting.meetingId="M";meeting.organizerId="U";meeting.title="Meeting";
        meeting.startTime=LocalDateTime.of(2099,1,1,9,15);meeting.endTime=meeting.startTime.plusHours(1);meeting.status="SCHEDULED";
        when(meetings.findById("M")).thenReturn(Optional.of(meeting));
        var u=new User();u.userId="U";u.status="ACTIVE";u.email="u@example.com";when(users.findById("U")).thenReturn(Optional.of(u));
        notification=new US16Notification();notification.meetingId="M";notification.userId="U";notification.channel="EMAIL";
        notification.type=MEETING_REMINDER_15_MINUTES;notification.scheduledAt=LocalDateTime.now(clock);notification.leadMinutes=15;
        when(notifications.findByStatusInOrderByScheduledAt(anyCollection())).thenReturn(List.of(notification));
    }
    @Test void sendsADueReminderOnlyOnce(){
        assertEquals(1,service.dispatchDue());assertEquals(SENT,notification.status);
        // Repository never returns sent rows during a later scan.
        when(notifications.findByStatusInOrderByScheduledAt(anyCollection())).thenReturn(List.of());
        assertEquals(0,service.dispatchDue());verify(email,times(1)).send(MEETING_REMINDER_15_MINUTES,meeting,"u@example.com");
    }
    @Test void cancelsReminderAfterMeetingCancellation(){
        meeting.status="CANCELLED";assertEquals(0,service.dispatchDue());assertEquals(CANCELLED,notification.status);verifyNoInteractions(email);
    }
    @Test void keepsFailedDeliveryRetryCountAndBackoff(){
        doThrow(new IllegalStateException("SMTP unavailable")).when(email).send(any(),any(),anyString());
        assertEquals(0,service.dispatchDue());assertEquals(FAILED,notification.status);assertEquals(1,notification.retryCount);
        assertEquals(LocalDateTime.now(clock).plusMinutes(5),notification.nextAttemptAt);
        assertEquals(0,service.dispatchDue());assertEquals(1,notification.retryCount);
    }
    @Test void appDeliveryDoesNotUseSmtp(){
        notification.channel="APP";assertEquals(1,service.dispatchDue());assertEquals(SENT,notification.status);verifyNoInteractions(email);
    }
}
