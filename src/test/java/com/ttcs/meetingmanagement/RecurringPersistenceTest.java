package com.ttcs.meetingmanagement;
import com.ttcs.meetingmanagement.dto.RecurringMeetingRequest;
import com.ttcs.meetingmanagement.recurrence.*;
import com.ttcs.meetingmanagement.repository.MeetingRepository;
import com.ttcs.meetingmanagement.room.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest @Transactional
class RecurringPersistenceTest {
 @Autowired RecurringMeetingService service;
 @Autowired MeetingRepository meetings;
 @Autowired RoomRepository rooms;
 @Autowired com.ttcs.meetingmanagement.repository.UserRepository users;
 @Autowired com.ttcs.meetingmanagement.repository.MeetingParticipantRepository participants;
 @Test void createsPersistedSeriesAndRejectsRoomConflict(){
 var organizer=new com.ttcs.meetingmanagement.model.User();organizer.userId="USER-1";organizer.email="organizer@example.com";users.save(organizer);
 var attendee=new com.ttcs.meetingmanagement.model.User();attendee.userId="USER-2";attendee.email="a@example.com";users.saveAndFlush(attendee);
 Room room=new Room();room.setRoomId("US03-test");room.setRoomName("Test room");room.setCapacity(8);room.setStatus("AVAILABLE");rooms.saveAndFlush(room);
 var request=new RecurringMeetingRequest("USER-1",room.getRoomId(),"US03 integration",null,OffsetDateTime.parse("2099-01-05T09:00:00+07:00"),OffsetDateTime.parse("2099-01-05T10:00:00+07:00"),RecurrenceFrequency.WEEKLY,2,4,List.of(1,3),null,null,List.of("a@example.com"));
 var result=service.create(request);meetings.flush();
 assertEquals(4,result.occurrences());assertEquals(4,result.meetingIds().size());
 for(String id:result.meetingIds()){var m=meetings.findById(id).orElseThrow();assertEquals(result.seriesId(),m.getRecurrenceSeriesId());assertEquals(room.getRoomId(),m.getRoomId());assertEquals(1, participants.findByMeetingId(id).size());}
 assertThrows(ResponseStatusException.class,()->service.create(request));
 }
}
