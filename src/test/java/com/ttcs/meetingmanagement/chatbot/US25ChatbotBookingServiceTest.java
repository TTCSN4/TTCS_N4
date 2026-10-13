package com.ttcs.meetingmanagement.chatbot;
import com.ttcs.meetingmanagement.model.*;
import com.ttcs.meetingmanagement.room.Room;
import com.ttcs.meetingmanagement.repository.*;
import com.ttcs.meetingmanagement.service.RoomBookingPermissionService;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class US25ChatbotBookingServiceTest {
    US25ChatRoomRepository rooms=mock(US25ChatRoomRepository.class);MeetingRepository meetings=mock(MeetingRepository.class);
    UserRepository users=mock(UserRepository.class);MeetingParticipantRepository participants=mock(MeetingParticipantRepository.class);
    RoomBookingPermissionService permissions=mock(RoomBookingPermissionService.class);
    US25ChatbotBookingService service=new US25ChatbotBookingService(rooms,meetings,users,participants,permissions);
    LocalDateTime start=LocalDateTime.of(2099,1,1,9,0);
    @BeforeEach void setup(){
        var u=new User();u.userId="U";u.status="ACTIVE";when(users.findById("U")).thenReturn(Optional.of(u));
        var room=new Room();room.roomId="R";room.roomName="Room";room.capacity=6;room.status="AVAILABLE";
        when(rooms.findAvailableForBooking(2)).thenReturn(List.of(room));
        when(meetings.saveAndFlush(any(Meeting.class))).thenAnswer(call->{Meeting m=call.getArgument(0);m.meetingId="M";return m;});
    }
    @Test void booksIntoSharedMeetingModelAndChecksRoomPermission(){
        var result=service.book(new US25ChatBookingRequest("U","Test",start,start.plusHours(1),2,null));
        assertEquals("M",result.meetingId());assertEquals("R",result.roomId());verify(permissions).assertCanBook("U","R");
    }
    @Test void rejectsRoomRaceConflict(){
        when(meetings.countRoomConflicts("R",null,start,start.plusHours(1))).thenReturn(1L);
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.book(new US25ChatBookingRequest("U","Test",start,start.plusHours(1),2,null))).getStatusCode().value());
        verify(meetings,never()).saveAndFlush(any());
    }
    @Test void rejectsInactiveOrganizer(){
        users.findById("U").orElseThrow().status="INACTIVE";
        assertEquals(400,assertThrows(ResponseStatusException.class,()->service.book(new US25ChatBookingRequest("U","Test",start,start.plusHours(1),2,null))).getStatusCode().value());
    }
}
