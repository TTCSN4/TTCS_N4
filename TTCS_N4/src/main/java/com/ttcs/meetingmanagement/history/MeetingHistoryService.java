package com.ttcs.meetingmanagement.history;
import com.ttcs.meetingmanagement.model.Meeting;
import com.ttcs.meetingmanagement.repository.MeetingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.OffsetDateTime;
import java.util.*;
@Service @Transactional(readOnly=true)
public class MeetingHistoryService {
 private final MeetingRepository meetings;private final HistoryCheckInRepository checkins;
 public MeetingHistoryService(MeetingRepository meetings,HistoryCheckInRepository checkins){this.meetings=meetings;this.checkins=checkins;}
 public List<Meeting> getHistory(String userId){
  if(userId==null||userId.isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"User ID is required.");
  var ids=new HashSet<>(checkins.checkedMeetingIds(userId.trim()));
  return meetings.findAllByEndTimeBeforeOrderByStartTimeDesc(OffsetDateTime.now()).stream().filter(m->ids.contains(m.getId())).toList();
 }
 public Meeting getHistoryDetail(String userId,Long meetingId){return getHistory(userId).stream().filter(m->m.getId().equals(meetingId)).findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Meeting not found in this user's history."));}
}
