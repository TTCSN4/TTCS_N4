package com.ttcs.meetingmanagement.meeting.update;
import com.ttcs.meetingmanagement.model.Meeting;
import com.ttcs.meetingmanagement.model.MeetingParticipant;
import com.ttcs.meetingmanagement.repository.*;
import com.ttcs.meetingmanagement.room.RoomRepository;
import com.ttcs.meetingmanagement.equipment.EquipmentReservationService;
import com.ttcs.meetingmanagement.equipment.EquipmentReservationService.Item;
import com.ttcs.meetingmanagement.service.RoomBookingPermissionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;

@Service
public class US02MeetingService {
    private final MeetingRepository meetings;
    private final RoomRepository rooms;
    private final EquipmentReservationService equipment;
    private final RoomBookingPermissionService permissions;
    private final MeetingParticipantRepository participants;
    private final UserRepository users;
    public US02MeetingService(MeetingRepository meetings,RoomRepository rooms,EquipmentReservationService equipment,
        RoomBookingPermissionService permissions,MeetingParticipantRepository participants,UserRepository users){
        this.meetings=meetings;this.rooms=rooms;this.equipment=equipment;this.permissions=permissions;this.participants=participants;this.users=users;
    }
    private LocalDateTime now(){return LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));}
    @Transactional(readOnly=true) public Meeting get(String id){return meetings.findById(id).orElseThrow(()->error(404,"Meeting not found"));}
    private void authorize(Meeting m,String actor){
        var user=users.findWithRole(actor).orElseThrow(()->error(403,"Active user required"));
        if(!"ACTIVE".equalsIgnoreCase(user.status))throw error(403,"Account is not active");
        boolean admin=user.role!=null&&"Admin".equalsIgnoreCase(user.role.getRoleName());
        if(!admin&&!Objects.equals(m.organizerId,actor))throw error(403,"Only organizer or Admin may modify meeting");
        if(!"SCHEDULED".equals(m.status)||!m.startTime.isAfter(now()))throw error(409,"Only future scheduled meetings can be modified");
    }
    @Transactional public Meeting update(String id,String actor,US02MeetingRequest r){
        Meeting selected=get(id);
        if(r==null||r.title()==null||r.title().isBlank()||r.title().length()>255||r.startTime()==null||r.endTime()==null||!r.endTime().isAfter(r.startTime())||!r.startTime().isAfter(now()))throw error(400,"Valid title and future time range are required");
        boolean series=r.scope()==US02MeetingRequest.Scope.SERIES;
        if(series&&selected.recurrenceSeriesId==null)throw error(409,"Meeting is not in a recurring series");
        List<Meeting> targets=series?meetings.lockSeries(selected.recurrenceSeriesId).stream().filter(m->"SCHEDULED".equals(m.status)&&m.startTime.isAfter(now())).toList()
            :List.of(meetings.findByIdForUpdate(id).orElseThrow(()->error(404,"Meeting not found")));
        targets=new ArrayList<>(targets);for(Meeting m:targets)authorize(m,actor);
        int index=0;while(index<targets.size()&&!targets.get(index).meetingId.equals(id))index++;
        if(index==targets.size())throw error(409,"Meeting can no longer be modified");
        String rule=r.recurrenceRule();
        if(!series&&rule!=null&&!rule.isBlank()&&!"NONE".equalsIgnoreCase(rule))throw error(400,"Change recurrence rules with SERIES scope");
        String frequency=null;int interval=1,count=targets.size();
        if(series&&rule!=null){
            if(rule.isBlank()||"NONE".equalsIgnoreCase(rule)){count=1;}
            else {
                Map<String,String> parts=new HashMap<>();
                for(String p:rule.toUpperCase(Locale.ROOT).split(";")){String[] pair=p.split("=",-1);if(pair.length==1&&Set.of("WEEKLY","MONTHLY").contains(p))parts.put("FREQ",p);else if(pair.length!=2||!Set.of("FREQ","INTERVAL","COUNT").contains(pair[0])||parts.putIfAbsent(pair[0],pair[1])!=null)throw error(400,"Use FREQ=WEEKLY|MONTHLY;INTERVAL=n;COUNT=n");}
                frequency=parts.get("FREQ");
                try{interval=Integer.parseInt(parts.getOrDefault("INTERVAL","1"));count=Integer.parseInt(parts.getOrDefault("COUNT",String.valueOf(count)));}catch(NumberFormatException e){throw error(400,"Invalid recurrence number");}
                if(!Set.of("WEEKLY","MONTHLY").contains(frequency==null?"":frequency)||interval<1||interval>52||count<1||count>104)throw error(400,"Invalid recurrence rule or count (1..104)");
                rule="FREQ="+frequency+";INTERVAL="+interval+";COUNT="+count;
            }
        }
        List<String> selectedPeople=people(selected.meetingId);
        List<Item> selectedEquipment=equipment.items(selected.meetingId);
        List<Meeting> removed=new ArrayList<>();
        if(count==1&&series&&frequency==null&&r.recurrenceRule()!=null){for(Meeting m:targets)if(!m.meetingId.equals(id))removed.add(m);targets=new ArrayList<>(List.of(selected));index=0;}
        else {while(targets.size()>count)removed.add(targets.remove(targets.size()-1));if(index>=targets.size())throw error(400,"COUNT excludes the selected occurrence");}
        while(targets.size()<count){Meeting m=new Meeting();m.meetingId=UUID.randomUUID().toString();m.organizerId=selected.organizerId;m.status="SCHEDULED";m.roomId=selected.roomId;m.recurrenceSeriesId=selected.recurrenceSeriesId;m.isRecurring=true;targets.add(m);}
        Map<String,List<Item>> items=new HashMap<>();Map<String,List<String>> people=new HashMap<>();
        Duration shift=Duration.between(selected.startTime,r.startTime()),duration=Duration.between(r.startTime(),r.endTime());
        LocalDateTime anchor=frequency==null?r.startTime():step(r.startTime(),frequency,-(long)index*interval);
        Set<String> ignored=new HashSet<>();targets.forEach(m->ignored.add(m.meetingId));removed.forEach(m->ignored.add(m.meetingId));
        // Lock rooms in deterministic order before modifying times.
        Set<String> roomIds=new TreeSet<>();for(Meeting m:targets){String room=r.roomId()==null?m.roomId:r.roomId().trim();if(room!=null&&!room.isBlank())roomIds.add(room);}
        for(String room:roomIds){var rm=rooms.findLockedById(room).orElseThrow(()->error(404,"Room not found"));if(!"AVAILABLE".equalsIgnoreCase(rm.status)&&!"ACTIVE".equalsIgnoreCase(rm.status))throw error(409,"Room is unavailable");permissions.assertCanBook(actor,room);}
        for(int i=0;i<targets.size();i++){
            Meeting m=targets.get(i);boolean fresh=m.startTime==null;
            items.put(m.meetingId,r.equipment()!=null?r.equipment():fresh?selectedEquipment:equipment.items(m.meetingId));
            List<String> ids=r.participantIds()!=null?r.participantIds():fresh?selectedPeople:people(m.meetingId);
            Set<String> unique=new LinkedHashSet<>();for(String userId:ids){if(userId==null||userId.isBlank())throw error(400,"Invalid participant ID");unique.add(userId.trim());}
            for(String userId:unique)if(!users.findById(userId).map(u->"ACTIVE".equalsIgnoreCase(u.status)).orElse(false))throw error(400,"Participant does not exist or is inactive: "+userId);
            people.put(m.meetingId,new ArrayList<>(unique));
            LocalDateTime start=frequency!=null?step(anchor,frequency,(long)i*interval):m.meetingId.equals(id)?r.startTime():m.startTime.plus(shift);
            if(!start.isAfter(now()))throw error(400,"Series edit would move a meeting into the past");
            m.startTime=start;m.endTime=start.plus(duration);m.title=r.title().trim();m.description=r.description();
            if(r.roomId()!=null)m.roomId=r.roomId().isBlank()?null:r.roomId().trim();
            if(r.recurrenceRule()!=null){m.recurrenceRule=frequency==null?null:rule;m.isRecurring=frequency!=null;if(frequency==null)m.recurrenceSeriesId=null;}
            if(!series&&m.recurrenceSeriesId!=null){m.recurrenceSeriesId=null;m.recurrenceRule=null;m.isRecurring=false;}
            if(m.roomId!=null){var room=rooms.findById(m.roomId).orElseThrow(()->error(404,"Room not found"));int size=unique.size()+(unique.contains(m.organizerId)?0:1);if(room.capacity!=null&&size>room.capacity)throw error(409,"Room capacity exceeded");}
        }
        List<Meeting> others=meetings.findAll().stream().filter(m->!ignored.contains(m.meetingId)&&!"CANCELLED".equals(m.status)).toList();
        for(int i=0;i<targets.size();i++){
            Meeting m=targets.get(i);
            for(Meeting other:others)checkConflict(m,people.get(m.meetingId),other,people(other.meetingId));
            for(int j=0;j<i;j++)checkConflict(m,people.get(m.meetingId),targets.get(j),people.get(targets.get(j).meetingId));
        }
        // Release all affected old allocations first; replacement checks the complete new schedule.
        for(Meeting m:targets)equipment.release(m.meetingId);
        for(Meeting m:removed){m.status="CANCELLED";m.cancelledAt=now();m.cancelReason="Recurrence rule changed";equipment.release(m.meetingId);meetings.save(m);}
        meetings.saveAllAndFlush(targets);
        for(Meeting m:targets){syncPeople(m.meetingId,people.get(m.meetingId));equipment.replace(m,items.get(m.meetingId));}
        return targets.stream().filter(m->m.meetingId.equals(id)).findFirst().orElseThrow();
    }
    private LocalDateTime step(LocalDateTime start,String frequency,long n){return "WEEKLY".equals(frequency)?start.plusWeeks(n):start.plusMonths(n);}
    private List<String> people(String id){return participants.findByMeetingId(id).stream().map(p->p.userId).toList();}
    private void checkConflict(Meeting m,List<String> ids,Meeting other,List<String> otherIds){
        if(!m.startTime.isBefore(other.endTime)||!m.endTime.isAfter(other.startTime))return;
        if(m.roomId!=null&&m.roomId.equals(other.roomId))throw error(409,"Room is already booked");
        Set<String> attendees=new HashSet<>(ids);attendees.add(m.organizerId);Set<String> otherPeople=new HashSet<>(otherIds);otherPeople.add(other.organizerId);
        if(attendees.stream().anyMatch(otherPeople::contains))throw error(409,"Participant has a conflicting meeting");
    }
    private void syncPeople(String id,List<String> ids){
        var old=participants.findByMeetingId(id);for(var p:old)if(!ids.contains(p.userId))participants.delete(p);
        for(String user:ids)if(old.stream().noneMatch(p->p.userId.equals(user))){var p=new MeetingParticipant();p.meetingId=id;p.userId=user;p.status="PENDING";participants.save(p);}
    }
    @Transactional public void cancel(String id,String actor,String reason){Meeting m=meetings.findByIdForUpdate(id).orElseThrow(()->error(404,"Meeting not found"));authorize(m,actor);m.status="CANCELLED";m.cancelReason=reason;m.cancelledAt=now();equipment.release(id);meetings.saveAndFlush(m);}
    private static ResponseStatusException error(int code,String message){return new ResponseStatusException(HttpStatus.valueOf(code),message);}
}
