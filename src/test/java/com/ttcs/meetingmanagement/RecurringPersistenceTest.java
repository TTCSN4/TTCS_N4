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
 @Test void createsPersistedSeriesAndRejectsRoomConflict(){
 Room room=new Room();room.setRoomId("US03-test");room.setRoomName("Test room");room.setCapacity(8);room.setStatus("AVAILABLE");rooms.saveAndFlush(room);
 var request=new RecurringMeetingRequest(1L,room.getRoomId(),"US03 integration",null,OffsetDateTime.parse("2099-01-05T09:00:00+07:00"),OffsetDateTime.parse("2099-01-05T10:00:00+07:00"),RecurrenceFrequency.WEEKLY,2,4,List.of(1,3),null,null,List.of("a@example.com"));
 var result=service.create(request);meetings.flush();
 assertEquals(4,result.occurrences());assertEquals(4,result.meetingIds().size());
 for(Long id:result.meetingIds()){var m=meetings.findById(id).orElseThrow();assertEquals(result.seriesId(),m.getRecurrenceSeriesId());assertEquals(room.getRoomId(),m.getRoom());assertEquals(List.of("a@example.com"),m.getParticipants());}
 assertThrows(ResponseStatusException.class,()->service.create(request));
 }
}
