package com.ttcs.meetingmanagement.notification.reminder;

import com.ttcs.meetingmanagement.model.*;
import com.ttcs.meetingmanagement.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import static com.ttcs.meetingmanagement.notification.reminder.US16NotificationStatus.*;
import static com.ttcs.meetingmanagement.notification.reminder.US16NotificationType.*;

@Service
class US16ReminderService {
    private final MeetingRepository meetings;private final MeetingParticipantRepository participants;
    private final UserRepository users;private final US16NotificationRepository notifications;
    private final US16EmailGateway email;private final Clock clock;private final int maxRetries;
    US16ReminderService(MeetingRepository meetings,MeetingParticipantRepository participants,UserRepository users,
        US16NotificationRepository notifications,US16EmailGateway email,Clock clock,@Value("${app.reminder.max-retries:3}")int maxRetries){
        this.meetings=meetings;this.participants=participants;this.users=users;this.notifications=notifications;
        this.email=email;this.clock=clock;this.maxRetries=maxRetries;
    }
    @Transactional
    US16Notification schedule(String meetingId,String actor,US16ReminderRequest request){
        Meeting m=meeting(meetingId);
        if(!actor.equals(m.organizerId))throw error(HttpStatus.FORBIDDEN,"Only the organizer can schedule reminders");
        if(!active(m)||!m.startTime.isAfter(now()))throw error(HttpStatus.CONFLICT,"Meeting is not a future active meeting");
        if(!eligibleRecipient(m,request.userId()))throw error(HttpStatus.BAD_REQUEST,"Recipient is not an active meeting member");
        LocalDateTime at=m.startTime.minusMinutes(request.minutesBefore());
        if(!at.isAfter(now()))throw error(HttpStatus.BAD_REQUEST,"Reminder must be scheduled in the future");
        String address=address(request.userId());
        if("EMAIL".equals(request.channel())&&!validEmail(address))throw error(HttpStatus.BAD_REQUEST,"Recipient has no valid email");
        if(request.email()!=null&&!request.email().equalsIgnoreCase(Objects.toString(address,"")))
            throw error(HttpStatus.BAD_REQUEST,"Use the recipient's registered email");
        return queue(m,request.userId(),request.channel(),MEETING_REMINDER_15_MINUTES,at,request.minutesBefore());
    }
    @Transactional
    int dispatchDue(){
        LocalDateTime current=now();
        for(Meeting m:meetings.findByStatusIn(List.of("SCHEDULED","CONFIRMED")))rebuild(m,current);
        int sent=0;
        var pending=notifications.findByStatusInOrderByScheduledAt(List.of(PENDING,FAILED));
        for(var n:pending){
            boolean due=n.status==PENDING?!n.scheduledAt.isAfter(current):n.nextAttemptAt!=null&&!n.nextAttemptAt.isAfter(current);
            if(!due||n.retryCount>=maxRetries)continue;
            var m=meetings.findById(n.meetingId).orElse(null);
            if(m==null||!eventAllowed(n,m,current)||!eligibleRecipient(m,n.userId)){
                n.status=CANCELLED;n.nextAttemptAt=null;continue;
            }
            if("APP".equals(n.channel)){n.status=SENT;n.sentAt=current;sent++;continue;}
            String registered=address(n.userId);
            if(!validEmail(registered)){n.status=CANCELLED;continue;}
            n.recipientEmail=registered;n.status=PROCESSING;n.retryCount++;n.lastAttemptAt=current;
            try{
                email.send(n.type,m,registered);n.status=SENT;n.sentAt=current;n.errorMessage=null;n.nextAttemptAt=null;sent++;
            }catch(RuntimeException ex){
                n.status=FAILED;String message=Objects.toString(ex.getMessage(),ex.getClass().getSimpleName());
                n.errorMessage=message.substring(0,Math.min(message.length(),1000));
                n.nextAttemptAt=n.retryCount<maxRetries?current.plusMinutes(Math.min(60,5L<<(Math.min(n.retryCount,5)-1))):null;
            }
        }
        notifications.saveAll(pending);return sent;
    }
    @Transactional(readOnly=true)
    List<US16Notification> list(String meetingId,String actor){
        Meeting m=meeting(meetingId);
        if(!eligibleRecipient(m,actor))throw error(HttpStatus.FORBIDDEN,"User is not a meeting member");
        return notifications.findByMeetingIdOrderByScheduledAt(meetingId).stream().filter(n->actor.equals(n.userId)).toList();
    }
    private void rebuild(Meeting m,LocalDateTime current){
        if(!active(m)||!m.startTime.isAfter(current))return;
        for(var n:notifications.findByMeetingIdOrderByScheduledAt(m.meetingId)){
            if((n.status==PENDING||n.status==FAILED)&&n.type==MEETING_REMINDER_15_MINUTES
                    &&!n.scheduledAt.equals(m.startTime.minusMinutes(n.leadMinutes))){
                n.status=CANCELLED;n.nextAttemptAt=null;
            }
        }
        LocalDateTime at=m.startTime.minusMinutes(15);
        Set<String> recipients=new LinkedHashSet<>();recipients.add(m.organizerId);
        participants.findByMeetingId(m.meetingId).stream().filter(p->Set.of("PENDING","INVITED","ACCEPTED").contains(p.status))
            .forEach(p->recipients.add(p.userId));
        for(String id:recipients)if(eligibleRecipient(m,id)){
            queue(m,id,"APP",MEETING_REMINDER_15_MINUTES,at,15);
            if(validEmail(address(id)))queue(m,id,"EMAIL",MEETING_REMINDER_15_MINUTES,at,15);
        }
    }
    private US16Notification queue(Meeting m,String userId,String channel,US16NotificationType type,LocalDateTime at,int lead){
        var existing=notifications.findByMeetingIdAndUserIdAndTypeAndChannelAndScheduledAt(m.meetingId,userId,type,channel,at);
        // Failed rows retain their retry count; scanning must not restart exhausted deliveries.
        if(existing.isPresent())return existing.get();
        var n=new US16Notification();n.meetingId=m.meetingId;n.userId=userId;n.type=type;n.channel=channel;
        n.recipientEmail=address(userId);n.scheduledAt=at;n.leadMinutes=lead;return notifications.save(n);
    }
    private boolean eventAllowed(US16Notification n,Meeting m,LocalDateTime current){
        if(n.type==MEETING_CANCELLED)return "CANCELLED".equals(m.status);
        return active(m)&&(n.type!=MEETING_REMINDER_15_MINUTES || current.isBefore(m.startTime)
            && n.scheduledAt.equals(m.startTime.minusMinutes(n.leadMinutes)));
    }
    private boolean eligibleRecipient(Meeting m,String id){
        if(id==null||!users.findById(id).map(u->"ACTIVE".equalsIgnoreCase(u.status)).orElse(false))return false;
        return id.equals(m.organizerId)||participants.findByMeetingIdAndUserId(m.meetingId,id)
            .map(p->Set.of("PENDING","INVITED","ACCEPTED").contains(p.status)).orElse(false);
    }
    private String address(String id){return users.findById(id).map(u->u.email).orElse(null);}
    private Meeting meeting(String id){return meetings.findById(id).orElseThrow(()->error(HttpStatus.NOT_FOUND,"Meeting not found"));}
    private boolean active(Meeting m){return Set.of("SCHEDULED","CONFIRMED").contains(m.status);}
    private LocalDateTime now(){return LocalDateTime.now(clock);}
    private static boolean validEmail(String s){return s!=null&&s.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");}
    private static ResponseStatusException error(HttpStatus status,String message){return new ResponseStatusException(status,message);}
}
