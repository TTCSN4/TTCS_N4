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
@SpringBootTest(classes=com.ttcs.meetingmanagement.MeetingmanagementApplication.class) @Transactional
class RecurringPersistenceTest {
 @Autowired RecurringMeetingService service;
 @Autowired MeetingRepository meetings;
 @Autowired RoomRepository rooms;
 @Autowired com.ttcs.meetingmanagement.repository.UserRepository users;
 @Test void createsPersistedSeriesAndRejectsRoomConflict(){
 Room room=new Room();room.setRoomId("US03-test");room.setRoomName("Test room");room.setCapacity(8);room.setStatus("AVAILABLE");rooms.saveAndFlush(room);
 var organizer=new com.ttcs.meetingmanagement.model.User();organizer.setUserId("1");organizer.setEmail("organizer@example.com");organizer.setFullName("Organizer");users.saveAndFlush(organizer);
 var request=new RecurringMeetingRequest("1",room.getRoomId(),"US03 integration",null,OffsetDateTime.parse("2099-01-05T09:00:00+07:00"),OffsetDateTime.parse("2099-01-05T10:00:00+07:00"),RecurrenceFrequency.WEEKLY,2,4,List.of(1,3),null,null,List.of("a@example.com"));
 var result=service.create(request);meetings.flush();
 assertEquals(4,result.createdCount());assertEquals(4,result.meetingIds().size());
 for(String id:result.meetingIds()){var m=meetings.findById(id).orElseThrow();assertEquals(result.recurrenceSeriesId(),m.getRecurrenceSeriesId());assertEquals(room.getRoomId(),m.getRoomId());}
 assertThrows(ResponseStatusException.class,()->service.create(request));
 }
}
