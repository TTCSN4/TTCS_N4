package com.ttcs.meetingmanagement.meeting.update;
import com.ttcs.meetingmanagement.model.Meeting;
import com.ttcs.meetingmanagement.model.User;
import com.ttcs.meetingmanagement.repository.*;
import com.ttcs.meetingmanagement.room.RoomRepository;
import com.ttcs.meetingmanagement.equipment.EquipmentReservationService;
import com.ttcs.meetingmanagement.service.RoomBookingPermissionService;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class US02MeetingServiceTest {
    MeetingRepository meetings=mock(MeetingRepository.class);
    RoomRepository rooms=mock(RoomRepository.class);
    EquipmentReservationService equipment=mock(EquipmentReservationService.class);
    MeetingParticipantRepository participants=mock(MeetingParticipantRepository.class);
    UserRepository users=mock(UserRepository.class);
    US02MeetingService service=new US02MeetingService(meetings,rooms,equipment,mock(RoomBookingPermissionService.class),participants,users);
    Meeting meeting;
    @BeforeEach void setup(){
        meeting=new Meeting();meeting.meetingId="M1";meeting.organizerId="U1";meeting.title="Old";meeting.status="SCHEDULED";
        meeting.startTime=LocalDateTime.of(2099,1,1,9,0);meeting.endTime=meeting.startTime.plusHours(1);
        when(meetings.findById("M1")).thenReturn(Optional.of(meeting));when(meetings.findByIdForUpdate("M1")).thenReturn(Optional.of(meeting));
        User u=new User();u.userId="U1";u.status="ACTIVE";when(users.findWithRole(anyString())).thenReturn(Optional.of(u));
    }
    @Test void rejectsAnotherOrganizer(){assertEquals(403,assertThrows(ResponseStatusException.class,()->service.cancel("M1","U2",null)).getStatusCode().value());verifyNoInteractions(equipment);}
    @Test void cancellationReleasesEquipment(){service.cancel("M1","U1","Changed");assertEquals("CANCELLED",meeting.status);verify(equipment).release("M1");}
    @Test void rejectsStartedMeeting(){meeting.startTime=LocalDateTime.now().minusMinutes(1);assertEquals(409,assertThrows(ResponseStatusException.class,()->service.cancel("M1","U1",null)).getStatusCode().value());}
    @Test void rejectsParticipantConflict(){
        Meeting other=new Meeting();other.meetingId="M2";other.organizerId="U1";other.status="SCHEDULED";other.startTime=meeting.startTime;other.endTime=meeting.endTime;
        when(meetings.findAll()).thenReturn(List.of(other));
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.update("M1","U1",new US02MeetingRequest("New",null,meeting.startTime,meeting.endTime,null))).getStatusCode().value());verify(equipment,never()).release(anyString());
    }
    @Test void preservesEquipmentWhenOptionalListOmitted(){
        var items=List.of(new EquipmentReservationService.Item("E1",2));when(equipment.items("M1")).thenReturn(items);
        service.update("M1","U1",new US02MeetingRequest("New",null,meeting.startTime.plusHours(2),meeting.endTime.plusHours(2),null));
        verify(equipment).replace(meeting,items);assertEquals("New",meeting.title);
    }
    @Test void instanceDetachesFromSeries(){meeting.recurrenceSeriesId="S1";meeting.isRecurring=true;service.update("M1","U1",new US02MeetingRequest("New",null,meeting.startTime,meeting.endTime,null));assertNull(meeting.recurrenceSeriesId);assertFalse(meeting.isRecurring);}
    @Test void seriesRecalculatesWeeklyOccurrences(){
        meeting.recurrenceSeriesId="S1";Meeting second=new Meeting();second.meetingId="M2";second.organizerId="U1";second.status="SCHEDULED";second.startTime=meeting.startTime.plusWeeks(1);second.endTime=second.startTime.plusHours(1);second.recurrenceSeriesId="S1";
        when(meetings.lockSeries("S1")).thenReturn(List.of(meeting,second));
        service.update("M1","U1",new US02MeetingRequest("New",null,meeting.startTime,meeting.endTime,null,null,null,"FREQ=WEEKLY;INTERVAL=2;COUNT=2",US02MeetingRequest.Scope.SERIES));
        assertEquals(meeting.startTime.plusWeeks(2),second.startTime);verify(equipment).replace(eq(second),any());
    }
    @Test void singleMeetingCanBecomeRecurring(){
        service.update("M1","U1",new US02MeetingRequest("New",null,meeting.startTime,meeting.endTime,null,null,null,"FREQ=WEEKLY;COUNT=2",US02MeetingRequest.Scope.SERIES));
        assertNotNull(meeting.recurrenceSeriesId);assertTrue(meeting.isRecurring);verify(equipment,times(2)).replace(any(),any());
    }
}
