package com.ttcs.meetingmanagement;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;

@SpringBootApplication @EnableScheduling
public class MeetingManagementApplication {
  public static void main(String[] args) { SpringApplication.run(MeetingManagementApplication.class, args); }
  @Bean Clock clock() { return Clock.systemUTC(); }
}

enum MeetingStatus { SCHEDULED, CONFIRMED, CANCELLED, COMPLETED }
enum ParticipantStatus { INVITED, ACCEPTED, DECLINED, REMOVED }
enum NotificationStatus { PENDING, PROCESSING, SENT, FAILED, CANCELLED }
enum NotificationType { MEETING_INVITATION, MEETING_REMINDER_15_MINUTES, MEETING_UPDATED, MEETING_CANCELLED }

@JsonAutoDetect(fieldVisibility=JsonAutoDetect.Visibility.ANY)
@Entity @Table(name="meetings") class Meeting {
  @Id @Column(name="meeting_id",length=36) String id;
  @Column(nullable=false) String title; @Column(columnDefinition="TEXT") String description; String location;
  @Column(name="online_url") String onlineUrl; @Column(name="detail_url") String detailUrl;
  @Column(name="start_time",nullable=false) OffsetDateTime startTime; @Column(name="end_time",nullable=false) OffsetDateTime endTime;
  @Column(name="organizer_id",nullable=false,length=36) String organizerId; @Column(name="organizer_name") String organizerName;
  @Column(name="organizer_email") String organizerEmail; @Enumerated(EnumType.STRING) @Column(nullable=false) MeetingStatus status=MeetingStatus.SCHEDULED;
  @Column(name="created_at",nullable=false) OffsetDateTime createdAt; @Column(name="updated_at",nullable=false) OffsetDateTime updatedAt;
  @PrePersist void create(){if(id==null)id=UUID.randomUUID().toString();createdAt=updatedAt=OffsetDateTime.now();}
  @PreUpdate void update(){updatedAt=OffsetDateTime.now();}
}

@JsonAutoDetect(fieldVisibility=JsonAutoDetect.Visibility.ANY)
@Entity @Table(name="meeting_participants",uniqueConstraints=@UniqueConstraint(columnNames={"meeting_id","user_id"})) class MeetingParticipant {
  @Id @Column(name="participant_id",length=36) String id; @Column(name="meeting_id",nullable=false,length=36) String meetingId;
  @Column(name="user_id",nullable=false,length=36) String userId; @Column(name="full_name") String fullName; @Column(nullable=false) String email;
  @Column(name="account_active",nullable=false) boolean accountActive=true;
  @Enumerated(EnumType.STRING) @Column(nullable=false) ParticipantStatus status=ParticipantStatus.INVITED;
  @PrePersist void create(){if(id==null)id=UUID.randomUUID().toString();}
}

@JsonAutoDetect(fieldVisibility=JsonAutoDetect.Visibility.ANY)
@Entity @Table(name="notifications",uniqueConstraints=@UniqueConstraint(name="uk_notification_meeting_user_type_time",columnNames={"meeting_id","user_id","type","scheduled_at"})) class Notification {
  @Id @Column(name="notification_id",length=36) String id; @Column(name="meeting_id",nullable=false,length=36) String meetingId;
  @Column(name="user_id",nullable=false,length=64) String userId; @Column(name="recipient_email") String recipientEmail;
  @Enumerated(EnumType.STRING) @Column(nullable=false) NotificationType type; @Column(nullable=false) String channel;
  @Column(name="scheduled_at",nullable=false) OffsetDateTime scheduledAt; @Column(name="sent_at") OffsetDateTime sentAt;
  @Column(name="last_attempt_at") OffsetDateTime lastAttemptAt; @Column(name="next_attempt_at") OffsetDateTime nextAttemptAt;
  @Column(name="retry_count",nullable=false) int retryCount; @Column(name="error_message",length=1000) String errorMessage;
  @Enumerated(EnumType.STRING) @Column(nullable=false) NotificationStatus status=NotificationStatus.PENDING;
  @PrePersist void create(){if(id==null)id=UUID.randomUUID().toString();}
}

interface MeetingRepository extends JpaRepository<Meeting,String>{}
interface ParticipantRepository extends JpaRepository<MeetingParticipant,String>{
  List<MeetingParticipant> findByMeetingId(String meetingId); Optional<MeetingParticipant> findByMeetingIdAndUserId(String meetingId,String userId);
}
interface NotificationRepository extends JpaRepository<Notification,String>{
  List<Notification> findByMeetingIdOrderByScheduledAt(String meetingId);
  Optional<Notification> findByMeetingIdAndUserIdAndTypeAndScheduledAt(String meetingId,String userId,NotificationType type,OffsetDateTime at);
  @Lock(LockModeType.PESSIMISTIC_WRITE) List<Notification> findByStatusInOrderByScheduledAt(Collection<NotificationStatus> statuses);
}

record ParticipantRequest(@NotBlank String userId,String fullName,@NotBlank @Email String email,Boolean accountActive){
  ParticipantRequest(String userId,String fullName,String email){this(userId,fullName,email,true);}
}
record MeetingRequest(@NotBlank String title,String description,@NotNull OffsetDateTime startTime,@NotNull OffsetDateTime endTime,@NotBlank String organizerId,
 String organizerName,@Email String organizerEmail,String location,String onlineUrl,String detailUrl,List<@Valid ParticipantRequest> participants){
  MeetingRequest(String title,String description,OffsetDateTime startTime,OffsetDateTime endTime,String organizerId){this(title,description,startTime,endTime,organizerId,null,null,null,null,null,List.of());}
}
record UpdateMeetingRequest(@NotNull OffsetDateTime startTime,@NotNull OffsetDateTime endTime,String location,String onlineUrl){}
record ReminderRequest(@NotBlank String userId,@Min(1) @Max(10080) int minutesBefore,@jakarta.validation.constraints.Pattern(regexp="EMAIL|APP") String channel,@Email String email){
  ReminderRequest(String userId,int minutesBefore,String channel){this(userId,minutesBefore,channel,null);}
}

interface EmailGateway{void send(NotificationType type,Meeting meeting,String recipient);}
@Service class SmtpEmailGateway implements EmailGateway{
  private final JavaMailSender sender;private final String from;
  SmtpEmailGateway(JavaMailSender sender,@Value("${app.mail.from:no-reply@meeting.local}")String from){this.sender=sender;this.from=from;}
  public void send(NotificationType type,Meeting m,String recipient){var mail=new SimpleMailMessage();mail.setFrom(from);mail.setTo(recipient);mail.setSubject(subject(type,m));mail.setText(body(type,m));sender.send(mail);}
  private String subject(NotificationType type,Meeting m){return switch(type){case MEETING_INVITATION->"Lời mời họp: "+m.title;case MEETING_UPDATED->"Lịch họp đã thay đổi: "+m.title;case MEETING_CANCELLED->"Lịch họp đã hủy: "+m.title;case MEETING_REMINDER_15_MINUTES->"Nhắc lịch họp: "+m.title;};}
  private String body(NotificationType type,Meeting m){var fmt=DateTimeFormatter.ofPattern("HH:mm, dd/MM/yyyy");String lead=switch(type){case MEETING_INVITATION->"Bạn được mời tham gia cuộc họp.";case MEETING_UPDATED->"Thông tin cuộc họp đã được cập nhật.";case MEETING_CANCELLED->"Cuộc họp này đã bị hủy.";case MEETING_REMINDER_15_MINUTES->"Bạn có cuộc họp sắp bắt đầu sau 15 phút.";};String place=hasText(m.location)?m.location:(hasText(m.onlineUrl)?m.onlineUrl:"Chưa xác định");return "Xin chào,\n\n"+lead+"\n\nCuộc họp: "+m.title+"\nThời gian: "+m.startTime.format(fmt)+" - "+m.endTime.format(fmt)+"\nĐịa điểm: "+place+"\nNgười tổ chức: "+Objects.toString(m.organizerName,m.organizerId)+"\nNội dung: "+Objects.toString(m.description,"")+"\nChi tiết: "+Objects.toString(m.detailUrl,"")+"\n\n"+(type==NotificationType.MEETING_CANCELLED?"Bạn không cần tham gia cuộc họp này.":"Vui lòng chuẩn bị và tham gia cuộc họp đúng giờ.");}
  private static boolean hasText(String s){return s!=null&&!s.isBlank();}
}

@Service class ReminderService{
  private static final Pattern EMAIL=Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
  private final MeetingRepository meetings;private final NotificationRepository notifications;private final ParticipantRepository participants;private final EmailGateway email;private final Clock clock;private final int maxRetries;
  ReminderService(MeetingRepository m,NotificationRepository n,ParticipantRepository p,EmailGateway e,Clock c,@Value("${app.reminder.max-retries:3}")int maxRetries){meetings=m;notifications=n;participants=p;email=e;clock=c;this.maxRetries=maxRetries;}

  @Transactional Meeting createMeeting(MeetingRequest r){validateTimes(r.startTime(),r.endTime());Meeting m=new Meeting();m.title=r.title().trim();m.description=r.description();m.startTime=r.startTime();m.endTime=r.endTime();m.organizerId=r.organizerId();m.organizerName=r.organizerName();m.organizerEmail=normalize(r.organizerEmail());m.location=r.location();m.onlineUrl=r.onlineUrl();m.detailUrl=r.detailUrl();meetings.save(m);if(r.participants()!=null)for(ParticipantRequest p:r.participants())addParticipant(m.id,p);rebuildReminders(m);return m;}
  @Transactional Meeting updateMeeting(String id,UpdateMeetingRequest r){validateTimes(r.startTime(),r.endTime());Meeting m=findMeeting(id);if(!isActive(m))throw conflict("Không thể sửa cuộc họp đã hủy hoặc hoàn thành");m.startTime=r.startTime();m.endTime=r.endTime();m.location=r.location();m.onlineUrl=r.onlineUrl();cancelOutstanding(id,n->n.type==NotificationType.MEETING_REMINDER_15_MINUTES||n.type==NotificationType.MEETING_UPDATED);queueRecipients(m,NotificationType.MEETING_UPDATED,true,now());rebuildReminders(m);return m;}
  @Transactional Meeting cancelMeeting(String id){Meeting m=findMeeting(id);if(m.status==MeetingStatus.CANCELLED)throw conflict("Cuộc họp đã bị hủy");if(m.status==MeetingStatus.COMPLETED)throw conflict("Không thể hủy cuộc họp đã hoàn thành");cancelOutstanding(id,n->true);m.status=MeetingStatus.CANCELLED;queueRecipients(m,NotificationType.MEETING_CANCELLED,true,now());return m;}
  @Transactional MeetingParticipant addParticipant(String meetingId,ParticipantRequest r){Meeting m=findMeeting(meetingId);if(!isActive(m))throw conflict("Không thể mời người tham dự vào cuộc họp đã hủy hoặc hoàn thành");MeetingParticipant p=participants.findByMeetingIdAndUserId(meetingId,r.userId()).orElseGet(MeetingParticipant::new);p.meetingId=meetingId;p.userId=r.userId();p.fullName=r.fullName();p.email=normalize(r.email());p.accountActive=r.accountActive()==null||r.accountActive();p.status=ParticipantStatus.INVITED;participants.save(p);cancelOutstanding(meetingId,n->n.userId.equals(p.userId)&&n.type==NotificationType.MEETING_INVITATION);if(eligible(p)){queue(m,p.userId,p.email,NotificationType.MEETING_INVITATION,"EMAIL",now(),true);OffsetDateTime at=m.startTime.minusMinutes(15);if(at.isAfter(now()))queue(m,p.userId,p.email,NotificationType.MEETING_REMINDER_15_MINUTES,"EMAIL",at,true);}return p;}
  @Transactional MeetingParticipant setParticipantStatus(String meetingId,String userId,ParticipantStatus status){findMeeting(meetingId);MeetingParticipant p=participants.findByMeetingIdAndUserId(meetingId,userId).orElseThrow(()->notFound("Không tìm thấy người tham dự"));p.status=status;if(status==ParticipantStatus.DECLINED||status==ParticipantStatus.REMOVED)cancelOutstanding(meetingId,n->n.userId.equals(userId));return p;}
  @Transactional Notification schedule(String meetingId,ReminderRequest r){Meeting m=findMeeting(meetingId);ensureSchedulable(m);OffsetDateTime at=m.startTime.minusMinutes(r.minutesBefore());if(!at.isAfter(now()))throw badRequest("Thời điểm nhắc phải ở tương lai");String recipient=r.email()!=null?normalize(r.email()):participants.findByMeetingIdAndUserId(meetingId,r.userId()).map(p->p.email).orElse(null);if("EMAIL".equals(r.channel())&&!validEmail(recipient))throw badRequest("Email người nhận không hợp lệ");return queue(m,r.userId(),recipient,NotificationType.MEETING_REMINDER_15_MINUTES,r.channel(),at,true);}

  @Scheduled(fixedDelayString="${app.reminder.scan-ms:60000}") @Transactional int dispatchDue(){OffsetDateTime current=now();int sent=0;for(Notification n:notifications.findByStatusInOrderByScheduledAt(List.of(NotificationStatus.PENDING,NotificationStatus.FAILED))){boolean due=n.status==NotificationStatus.PENDING?!n.scheduledAt.isAfter(current):n.nextAttemptAt!=null&&!n.nextAttemptAt.isAfter(current);if(due&&dispatch(n,current))sent++;}return sent;}
  private boolean dispatch(Notification n,OffsetDateTime current){if(n.retryCount>=maxRetries)return false;Optional<Meeting> found=meetings.findById(n.meetingId);if(found.isEmpty()||!eventAllowed(n.type,found.get(),current)||!recipientEligible(n)){n.status=NotificationStatus.CANCELLED;return false;}if(!"EMAIL".equals(n.channel)){n.status=NotificationStatus.SENT;n.sentAt=current;return true;}if(!validEmail(n.recipientEmail)){n.status=NotificationStatus.CANCELLED;n.errorMessage="Email người nhận không hợp lệ";return false;}n.status=NotificationStatus.PROCESSING;n.lastAttemptAt=current;n.retryCount++;try{email.send(n.type,found.get(),n.recipientEmail);n.status=NotificationStatus.SENT;n.sentAt=current;n.nextAttemptAt=null;n.errorMessage=null;return true;}catch(RuntimeException ex){n.status=NotificationStatus.FAILED;n.errorMessage=message(ex);if(n.retryCount<maxRetries)n.nextAttemptAt=current.plusMinutes(Math.min(60,5L<<(n.retryCount-1)));return false;}}
  private boolean eventAllowed(NotificationType type,Meeting m,OffsetDateTime current){if(type==NotificationType.MEETING_CANCELLED)return m.status==MeetingStatus.CANCELLED;if(!isActive(m))return false;return type!=NotificationType.MEETING_REMINDER_15_MINUTES||current.isBefore(m.startTime);}
  private boolean recipientEligible(Notification n){if(n.userId.equals(organizerKey(n.meetingId)))return true;return participants.findByMeetingIdAndUserId(n.meetingId,n.userId).map(this::eligible).orElse(false);}
  private boolean eligible(MeetingParticipant p){return p.accountActive&&validEmail(p.email)&&(p.status==ParticipantStatus.INVITED||p.status==ParticipantStatus.ACCEPTED);}
  private void rebuildReminders(Meeting m){if(!isActive(m))return;OffsetDateTime at=m.startTime.minusMinutes(15);if(!at.isAfter(now()))return;if(validEmail(m.organizerEmail))queue(m,organizerKey(m.id),m.organizerEmail,NotificationType.MEETING_REMINDER_15_MINUTES,"EMAIL",at,true);for(MeetingParticipant p:participants.findByMeetingId(m.id))if(eligible(p))queue(m,p.userId,p.email,NotificationType.MEETING_REMINDER_15_MINUTES,"EMAIL",at,true);}
  private void queueRecipients(Meeting m,NotificationType type,boolean organizer,OffsetDateTime at){if(organizer&&validEmail(m.organizerEmail))queue(m,organizerKey(m.id),m.organizerEmail,type,"EMAIL",at,true);for(MeetingParticipant p:participants.findByMeetingId(m.id))if(eligible(p))queue(m,p.userId,p.email,type,"EMAIL",at,true);}
  private Notification queue(Meeting m,String user,String address,NotificationType type,String channel,OffsetDateTime at,boolean reactivate){Optional<Notification> old=notifications.findByMeetingIdAndUserIdAndTypeAndScheduledAt(m.id,user,type,at);if(old.isPresent()){Notification n=old.get();if(reactivate&&(n.status==NotificationStatus.CANCELLED||n.status==NotificationStatus.FAILED)){n.recipientEmail=address;n.channel=channel;n.status=NotificationStatus.PENDING;n.retryCount=0;n.sentAt=null;n.lastAttemptAt=null;n.nextAttemptAt=null;n.errorMessage=null;}return n;}Notification n=new Notification();n.meetingId=m.id;n.userId=user;n.recipientEmail=address;n.type=type;n.channel=channel;n.scheduledAt=at;return notifications.save(n);}
  private void cancelOutstanding(String meetingId,Predicate<Notification> predicate){notifications.findByMeetingIdOrderByScheduledAt(meetingId).stream().filter(n->n.status==NotificationStatus.PENDING||n.status==NotificationStatus.FAILED).filter(predicate).forEach(n->{n.status=NotificationStatus.CANCELLED;n.nextAttemptAt=null;});}
  private void ensureSchedulable(Meeting m){if(!isActive(m))throw conflict("Chỉ có thể nhắc cuộc họp đang lên lịch hoặc đã xác nhận");if(!m.startTime.isAfter(now()))throw conflict("Cuộc họp đã bắt đầu");}
  private boolean isActive(Meeting m){return m.status==MeetingStatus.SCHEDULED||m.status==MeetingStatus.CONFIRMED;}private Meeting findMeeting(String id){return meetings.findById(id).orElseThrow(()->notFound("Không tìm thấy cuộc họp"));}
  private void validateTimes(OffsetDateTime start,OffsetDateTime end){if(!end.isAfter(start))throw badRequest("Thời gian kết thúc phải sau thời gian bắt đầu");if(!start.isAfter(now()))throw badRequest("Thời gian bắt đầu phải ở tương lai");}
  private OffsetDateTime now(){return OffsetDateTime.now(clock);}private static String organizerKey(String id){return "organizer:"+id;}private static String normalize(String s){return s==null?null:s.trim().toLowerCase(Locale.ROOT);}private static boolean validEmail(String s){return s!=null&&EMAIL.matcher(s).matches();}
  private static String message(Exception e){String s=Objects.toString(e.getMessage(),e.getClass().getSimpleName());return s.substring(0,Math.min(1000,s.length()));}private static ResponseStatusException badRequest(String s){return new ResponseStatusException(HttpStatus.BAD_REQUEST,s);}private static ResponseStatusException conflict(String s){return new ResponseStatusException(HttpStatus.CONFLICT,s);}private static ResponseStatusException notFound(String s){return new ResponseStatusException(HttpStatus.NOT_FOUND,s);}
}

@RestController @RequestMapping("/api") class ReminderController{
  private final ReminderService service;private final MeetingRepository meetings;private final NotificationRepository notifications;
  ReminderController(ReminderService s,MeetingRepository m,NotificationRepository n){service=s;meetings=m;notifications=n;}
  @PostMapping("/meetings") @ResponseStatus(HttpStatus.CREATED) Meeting create(@Valid@RequestBody MeetingRequest r){return service.createMeeting(r);}@GetMapping("/meetings")List<Meeting>meetings(){return meetings.findAll();}
  @PutMapping("/meetings/{id}")Meeting update(@PathVariable String id,@Valid@RequestBody UpdateMeetingRequest r){return service.updateMeeting(id,r);}@PostMapping("/meetings/{id}/cancel")Meeting cancel(@PathVariable String id){return service.cancelMeeting(id);}
  @PostMapping("/meetings/{id}/participants")@ResponseStatus(HttpStatus.CREATED)MeetingParticipant participant(@PathVariable String id,@Valid@RequestBody ParticipantRequest r){return service.addParticipant(id,r);}@PatchMapping("/meetings/{id}/participants/{userId}/{status}")MeetingParticipant participantStatus(@PathVariable String id,@PathVariable String userId,@PathVariable ParticipantStatus status){return service.setParticipantStatus(id,userId,status);}
  @PostMapping("/meetings/{id}/reminders")@ResponseStatus(HttpStatus.CREATED)Notification remind(@PathVariable String id,@Valid@RequestBody ReminderRequest r){return service.schedule(id,r);}@GetMapping("/meetings/{id}/reminders")List<Notification>reminders(@PathVariable String id){return notifications.findByMeetingIdOrderByScheduledAt(id);}
  @PostMapping("/reminders/dispatch")Map<String,Integer>dispatch(){return Map.of("sent",service.dispatchDue());}
}
