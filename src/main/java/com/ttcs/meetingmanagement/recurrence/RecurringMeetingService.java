package com.ttcs.meetingmanagement.recurrence;
import com.ttcs.meetingmanagement.dto.*;
import com.ttcs.meetingmanagement.model.*;
import com.ttcs.meetingmanagement.repository.MeetingRepository;
import com.ttcs.meetingmanagement.room.Room;
import jakarta.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
@Service
public class RecurringMeetingService {
 private final EntityManager em;private final MeetingRepository meetings;
 public RecurringMeetingService(EntityManager em,MeetingRepository meetings){this.em=em;this.meetings=meetings;}
 @Transactional public RecurringMeetingResponse create(RecurringMeetingRequest r){
  List<RecurringScheduleCalculator.Occurrence> slots;
  try{slots=new RecurringScheduleCalculator().generate(r);}catch(IllegalArgumentException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,e.getMessage());}
  if(!slots.getFirst().startTime().isAfter(OffsetDateTime.now()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Start must be in the future.");
  Room room=em.find(Room.class,r.roomId().trim(),LockModeType.PESSIMISTIC_WRITE);
  if(room==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Room not found.");
  List<String> participants=r.participants()==null?List.of():r.participants().stream().map(String::trim).toList();
  if(participants.size()>room.getCapacity())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Participants exceed room capacity.");
  if(participants.stream().map(String::toLowerCase).distinct().count()!=participants.size())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Duplicate participant emails.");
  var existing=meetings.findAllByOrderByStartTimeAsc();
  for(var slot:slots){
   if(meetings.countRoomConflicts(room.getRoomId(),-1L,MeetingStatus.CANCELLED,slot.startTime(),slot.endTime())>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Room conflict at "+slot.startTime());
   boolean conflict=existing.stream().filter(m->m.getStatus()!=MeetingStatus.CANCELLED).filter(m->m.getStartTime().isBefore(slot.endTime())&&m.getEndTime().isAfter(slot.startTime())).anyMatch(m->m.getParticipants().stream().anyMatch(participants::contains));
   if(conflict)throw new ResponseStatusException(HttpStatus.CONFLICT,"Participant conflict at "+slot.startTime());
  }
  Long seriesId=null;List<Long> ids=new ArrayList<>();
  for(var slot:slots){Meeting m=new Meeting();m.setTitle(r.title().trim());m.setDescription(r.description());m.setRoom(room.getRoomId());m.setStartTime(slot.startTime());m.setEndTime(slot.endTime());m.setOrganizerId(r.organizerId()==null?1L:r.organizerId());m.setParticipants(new ArrayList<>(participants));m.setStatus(MeetingStatus.SCHEDULED);m.setCreatedAt(OffsetDateTime.now());m.setUpdatedAt(OffsetDateTime.now());meetings.save(m);if(seriesId==null)seriesId=m.getId();m.setRecurrenceSeriesId(seriesId);ids.add(m.getId());}
  String rule="frequency="+r.frequency()+";interval="+(r.interval()==null?1:r.interval())+";weekdays="+r.weekdays()+";monthDay="+r.monthDay()+";until="+r.until()+";count="+r.occurrences();
  MeetingRecurrence config=new MeetingRecurrence();config.id=seriesId;config.configuration=rule;config.occurrences=ids.size();em.persist(config);
  return new RecurringMeetingResponse(seriesId,rule,ids.size(),ids);
 }
}
