package com.ttcs.meetingmanagement.meeting.availability;
import com.ttcs.meetingmanagement.model.*;
import com.ttcs.meetingmanagement.repository.UserRepository;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static com.ttcs.meetingmanagement.meeting.availability.US05AvailabilityDtos.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class US05AvailabilityServiceTest {
    UserRepository users=mock(UserRepository.class);US05AvailabilityRepository meetings=mock(US05AvailabilityRepository.class);
    US05AvailabilityService service=new US05AvailabilityService(users,meetings);
    LocalDate date=LocalDate.of(2099,1,2);
    void active(){var u=new User();u.userId="U1";u.status="ACTIVE";when(users.findAllById(List.of("U1"))).thenReturn(List.of(u));}
    Meeting meeting(LocalDateTime start,LocalDateTime end){var m=new Meeting();m.startTime=start;m.endTime=end;return m;}
    @Test void clipsOvernightBusyTimeAgainstRequestedDay(){
        active();when(meetings.busy(anyList(),any(),any())).thenReturn(List.of(meeting(date.minusDays(1).atTime(23,0),date.atTime(9,0))));
        var result=service.find(new AvailabilityRequest(date,List.of("U1"),60));
        assertEquals("09:00",result.slots().getFirst().start());assertEquals(8,result.count());
    }
    @Test void mergesOverlappingBusyIntervals(){
        active();when(meetings.busy(anyList(),any(),any())).thenReturn(List.of(meeting(date.atTime(9,0),date.atTime(11,0)),meeting(date.atTime(10,0),date.atTime(12,0))));
        var result=service.find(new AvailabilityRequest(date,List.of("U1"),60));
        assertEquals(List.of("08:00","12:00","13:00","14:00","15:00","16:00"),result.slots().stream().map(SlotResponse::start).toList());
    }
    @Test void rejectsUnknownParticipant(){assertThrows(IllegalArgumentException.class,()->service.find(new AvailabilityRequest(date,List.of("missing"),60)));}
}
