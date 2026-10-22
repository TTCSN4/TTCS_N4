package com.ttcs.meetingmanagement.meeting.update;

import com.ttcs.meetingmanagement.model.*;
import com.ttcs.meetingmanagement.repository.*;
import com.ttcs.meetingmanagement.room.*;
import com.ttcs.meetingmanagement.service.RoomBookingPermissionService;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class US02MeetingServiceTest {
    MeetingRepository meetings = mock(MeetingRepository.class);
    RoomRepository rooms = mock(RoomRepository.class);
    EquipmentBookingRepository equipment = mock(EquipmentBookingRepository.class);
    US02MeetingService service = new US02MeetingService(meetings, rooms, equipment, mock(RoomBookingPermissionService.class));
    Meeting meeting;
    @BeforeEach void setUp() {
        meeting = new Meeting(); meeting.meetingId = "M1"; meeting.organizerId = "U1";
        meeting.startTime = LocalDateTime.of(2099, 1, 1, 9, 0); meeting.endTime = meeting.startTime.plusHours(1);
        meeting.status = "SCHEDULED";
        when(meetings.findByIdForUpdate("M1")).thenReturn(Optional.of(meeting));
        when(equipment.findAllByMeetingId("M1")).thenReturn(List.of());
        when(meetings.saveAndFlush(meeting)).thenReturn(meeting);
    }
    @Test void rejectsAnotherOrganizer() {
        var ex = assertThrows(ResponseStatusException.class, () -> service.cancel("M1", "U2", null));
        assertEquals(403, ex.getStatusCode().value()); assertEquals("SCHEDULED", meeting.status);
    }
    @Test void cancellationReleasesEquipment() {
        var reservation = new EquipmentBooking(); reservation.setStatus(EquipmentBookingStatus.BOOKED);
        when(equipment.findAllByMeetingId("M1")).thenReturn(List.of(reservation));
        service.cancel("M1", "U1", "Changed plans");
        assertEquals("CANCELLED", meeting.status); assertNotNull(meeting.cancelledAt);
        assertEquals(EquipmentBookingStatus.CANCELLED, reservation.getStatus());
    }
    @Test void rejectsStartedMeeting() {
        meeting.startTime = LocalDateTime.now().minusMinutes(1);
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.cancel("M1", "U1", null)).getStatusCode().value());
    }
    @Test void rejectsRoomConflictWithoutChangingMeeting() {
        var room = new Room(); room.setRoomId("R1");
        when(rooms.findLockedById("R1")).thenReturn(Optional.of(room));
        var request = new US02MeetingRequest("New title", null, meeting.startTime.plusHours(2), meeting.endTime.plusHours(2), "R1");
        when(meetings.countRoomConflicts("R1", "M1", request.startTime(), request.endTime())).thenReturn(1L);
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.update("M1", "U1", request)).getStatusCode().value());
        assertNull(meeting.title);
    }
}
