package com.ttcs.meetingmanagement;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties={
  "spring.datasource.url=jdbc:h2:mem:us16test;MODE=MySQL;DB_CLOSE_DELAY=-1",
  "spring.datasource.driver-class-name=org.h2.Driver",
  "spring.jpa.hibernate.ddl-auto=create-drop",
  "app.reminder.scan-ms=3600000",
  "app.reminder.max-retries=3"
})
@Import(ReminderServiceTest.Config.class)
class ReminderServiceTest {
  @Autowired ReminderService service;
  @Autowired MeetingRepository meetings;
  @Autowired ParticipantRepository participants;
  @Autowired NotificationRepository notifications;
  @Autowired MutableClock clock;
  @Autowired FakeEmailGateway mail;

  @BeforeEach void reset(){notifications.deleteAll();participants.deleteAll();meetings.deleteAll();clock.set(OffsetDateTime.parse("2026-10-12T13:30:00+07:00").toInstant());mail.reset();}

  @Test void sendsInvitationImmediatelyButNotReminderThirtyMinutesEarly(){Meeting m=createAt(clock.now().plusSeconds(30*60));service.dispatchDue();assertThat(mail.types).containsExactly(NotificationType.MEETING_INVITATION);assertThat(mail.recipients).containsExactly("member@example.com");assertThat(sent(m,NotificationType.MEETING_REMINDER_15_MINUTES)).isZero();}

  @Test void sendsReminderAtFifteenMinutesAndNeverDuplicates(){Meeting m=createAt(clock.now().plusSeconds(30*60));service.dispatchDue();clock.advance(Duration.ofMinutes(15));service.dispatchDue();service.dispatchDue();assertThat(sent(m,NotificationType.MEETING_REMINDER_15_MINUTES)).isEqualTo(2);assertThat(mail.types.stream().filter(t->t==NotificationType.MEETING_REMINDER_15_MINUTES)).hasSize(2);}

  @Test void cancelledMeetingOnlySendsCancellation(){Meeting m=createAt(clock.now().plusSeconds(30*60));service.cancelMeeting(m.id);service.dispatchDue();assertThat(mail.types).containsExactly(NotificationType.MEETING_CANCELLED,NotificationType.MEETING_CANCELLED);assertThat(pending(m,NotificationType.MEETING_REMINDER_15_MINUTES)).isZero();}

  @Test void declinedOrRemovedParticipantReceivesNothing(){Meeting m=createAt(clock.now().plusSeconds(30*60));service.setParticipantStatus(m.id,"member-1",ParticipantStatus.DECLINED);service.dispatchDue();clock.advance(Duration.ofMinutes(15));service.dispatchDue();assertThat(mail.recipients).doesNotContain("member@example.com");assertThat(mail.recipients).containsExactly("organizer@example.com");}

  @Test void inactiveAccountIsExcluded(){Meeting m=createAt(clock.now().plusSeconds(30*60),false);service.dispatchDue();clock.advance(Duration.ofMinutes(15));service.dispatchDue();assertThat(mail.recipients).containsExactly("organizer@example.com");assertThat(notifications.findByMeetingIdOrderByScheduledAt(m.id)).noneMatch(n->n.userId.equals("member-1"));}

  @Test void smtpFailureIsRecordedAndRetriedAfterBackoff(){Meeting m=createOrganizerOnly(clock.now().plusSeconds(16*60));clock.advance(Duration.ofMinutes(1));mail.failures=1;service.dispatchDue();Notification n=only(m,NotificationType.MEETING_REMINDER_15_MINUTES);assertThat(n.status).isEqualTo(NotificationStatus.FAILED);assertThat(n.retryCount).isEqualTo(1);assertThat(n.errorMessage).contains("SMTP unavailable");clock.advance(Duration.ofMinutes(5));service.dispatchDue();n=only(m,NotificationType.MEETING_REMINDER_15_MINUTES);assertThat(n.status).isEqualTo(NotificationStatus.SENT);assertThat(n.retryCount).isEqualTo(2);}

  @Test void rescheduleCancelsOldReminderCreatesNewReminderAndUpdateMail(){Meeting m=createAt(clock.now().plusSeconds(60*60));OffsetDateTime oldAt=m.startTime.minusMinutes(15);OffsetDateTime newStart=OffsetDateTime.ofInstant(clock.now().plusSeconds(120*60),ZoneOffset.ofHours(7));service.updateMeeting(m.id,new UpdateMeetingRequest(newStart,newStart.plusHours(1),"B202",null));service.dispatchDue();assertThat(notifications.findByMeetingIdOrderByScheduledAt(m.id)).anyMatch(n->n.type==NotificationType.MEETING_REMINDER_15_MINUTES&&n.scheduledAt.equals(oldAt)&&n.status==NotificationStatus.CANCELLED).anyMatch(n->n.type==NotificationType.MEETING_REMINDER_15_MINUTES&&n.scheduledAt.equals(newStart.minusMinutes(15))&&n.status==NotificationStatus.PENDING);assertThat(mail.types.stream().filter(t->t==NotificationType.MEETING_UPDATED)).hasSize(2);}

  @Test void reinvitingParticipantReactivatesCancelledReminder(){Meeting m=createAt(clock.now().plusSeconds(60*60));service.setParticipantStatus(m.id,"member-1",ParticipantStatus.REMOVED);service.addParticipant(m.id,new ParticipantRequest("member-1","Member","member@example.com",true));List<Notification> reminders=notifications.findByMeetingIdOrderByScheduledAt(m.id).stream().filter(n->n.type==NotificationType.MEETING_REMINDER_15_MINUTES&&n.userId.equals("member-1")).toList();assertThat(reminders).hasSize(1);assertThat(reminders.getFirst().status).isEqualTo(NotificationStatus.PENDING);}

  private Meeting createAt(Instant start){return createAt(start,true);}
  private Meeting createAt(Instant start,boolean active){OffsetDateTime s=OffsetDateTime.ofInstant(start,ZoneOffset.ofHours(7));return service.createMeeting(new MeetingRequest("Họp dự án","Tổng kết sprint",s,s.plusHours(1),"organizer-1","Nguyễn Văn A","organizer@example.com","A101",null,"http://localhost:8080/meetings/1",List.of(new ParticipantRequest("member-1","Member","member@example.com",active))));}
  private Meeting createOrganizerOnly(Instant start){OffsetDateTime s=OffsetDateTime.ofInstant(start,ZoneOffset.ofHours(7));return service.createMeeting(new MeetingRequest("Họp dự án",null,s,s.plusHours(1),"organizer-1","Nguyễn Văn A","organizer@example.com","A101",null,null,List.of()));}
  private long sent(Meeting m,NotificationType type){return notifications.findByMeetingIdOrderByScheduledAt(m.id).stream().filter(n->n.type==type&&n.status==NotificationStatus.SENT).count();}
  private long pending(Meeting m,NotificationType type){return notifications.findByMeetingIdOrderByScheduledAt(m.id).stream().filter(n->n.type==type&&n.status==NotificationStatus.PENDING).count();}
  private Notification only(Meeting m,NotificationType type){return notifications.findByMeetingIdOrderByScheduledAt(m.id).stream().filter(n->n.type==type).findFirst().orElseThrow();}

  @TestConfiguration static class Config{
    @Bean @Primary MutableClock mutableClock(){return new MutableClock();}
    @Bean @Primary FakeEmailGateway fakeEmailGateway(){return new FakeEmailGateway();}
  }
  static class MutableClock extends Clock{
    private Instant instant=Instant.EPOCH;void set(Instant value){instant=value;}void advance(Duration value){instant=instant.plus(value);}Instant now(){return instant;}
    public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return instant;}
  }
  static class FakeEmailGateway implements EmailGateway{
    final List<NotificationType>types=new ArrayList<>();final List<String>recipients=new ArrayList<>();int failures;
    public void send(NotificationType type,Meeting meeting,String recipient){if(failures-->0)throw new IllegalStateException("SMTP unavailable");types.add(type);recipients.add(recipient);}void reset(){types.clear();recipients.clear();failures=0;}
  }
}
