package com.ttcs.meetingmanagement;
import com.ttcs.meetingmanagement.history.*;
import com.ttcs.meetingmanagement.model.*;
import com.ttcs.meetingmanagement.repository.MeetingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.OffsetDateTime;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest @Transactional
class HistoryIntegrationTest {
 @Autowired MeetingRepository meetings;
 @Autowired HistoryCheckInRepository checkins;
 @Autowired MeetingHistoryService service;
 Meeting create(OffsetDateTime start,OffsetDateTime end){var m=new Meeting();m.setTitle("History integration");m.setStartTime(start);m.setEndTime(end);m.setOrganizerId(1L);m.setStatus(MeetingStatus.SCHEDULED);m.setCreatedAt(OffsetDateTime.now());m.setUpdatedAt(OffsetDateTime.now());return meetings.saveAndFlush(m);}
 void check(Meeting m,String user){var c=new HistoryCheckIn();c.meetingId=m.getId();c.userId=user;c.checkInTime=OffsetDateTime.now().minusDays(2);checkins.saveAndFlush(c);}
 @Test void onlyEndedAndCheckedInMeetingsAreReturned(){
 var now=OffsetDateTime.now();var past=create(now.minusDays(2),now.minusDays(2).plusHours(1));var future=create(now.plusDays(2),now.plusDays(2).plusHours(1));var noCheck=create(now.minusDays(3),now.minusDays(3).plusHours(1));check(past,"US06-test");check(future,"US06-test");
 var result=service.getHistory("US06-test");assertEquals(1,result.size());assertEquals(past.getId(),result.getFirst().getId());assertEquals(past.getId(),service.getHistoryDetail("US06-test",past.getId()).getId());
 assertTrue(service.getHistory("other-user").isEmpty());assertThrows(ResponseStatusException.class,()->service.getHistoryDetail("other-user",past.getId()));assertThrows(ResponseStatusException.class,()->service.getHistoryDetail("US06-test",noCheck.getId()));
 }
}
