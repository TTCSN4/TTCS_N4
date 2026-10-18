package com.ttcs.meetingmanagement.roombooking;

import com.ttcs.meetingmanagement.meeting.update.US02MeetingService;
import com.ttcs.meetingmanagement.model.*;
import com.ttcs.meetingmanagement.repository.*;
import com.ttcs.meetingmanagement.room.RoomRepository;
import com.ttcs.meetingmanagement.service.RoomBookingPermissionService;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class US09CancellationServiceTest {
    MeetingRepository meetings;
    EquipmentBookingRepository equipmentBookings;
    RoomBookingService roomService;
    US02MeetingService meetingService;
    Meeting meeting;

    @BeforeEach void setUp() {
        meetings = mock(MeetingRepository.class);
        equipmentBookings = mock(EquipmentBookingRepository.class);
        roomService = new RoomBookingService(meetings, equipmentBookings,
                mock(RoomRepository.class), mock(RoomBookingPermissionService.class));
        meetingService = new US02MeetingService(meetings, mock(RoomRepository.class),
                equipmentBookings, mock(RoomBookingPermissionService.class));
        meeting = meeting("SCHEDULED");
        when(meetings.findByIdForUpdate("M-09")).thenReturn(Optional.of(meeting));
        when(equipmentBookings.findAllByMeetingId("M-09")).thenReturn(List.of());
        when(meetings.saveAndFlush(meeting)).thenReturn(meeting);
    }

    @Test void cancelRoomKeepsMeetingAndReleasesOnlyBookedEquipment() {
        var booked = booking(EquipmentBookingStatus.BOOKED);
        var maintenance = booking(EquipmentBookingStatus.MAINTENANCE);
        when(equipmentBookings.findAllByMeetingId("M-09")).thenReturn(List.of(booked, maintenance));
        var response = roomService.cancelRoomBooking("M-09", "OWNER");
        assertNull(meeting.roomId); assertEquals("SCHEDULED", meeting.status);
        assertEquals("R-01", response.getRoomId());
        assertEquals(EquipmentBookingStatus.CANCELLED, booked.getStatus());
        assertEquals(EquipmentBookingStatus.MAINTENANCE, maintenance.getStatus());
        verify(meetings).saveAndFlush(meeting);
    }

    @Test void cancelMeetingRecordsHistoryAndLeavesIndependentBookingsUntouched() {
        var booked = booking(EquipmentBookingStatus.BOOKED);
        var maintenance = booking(EquipmentBookingStatus.MAINTENANCE);
        var otherMeeting = booking(EquipmentBookingStatus.BOOKED);
        when(equipmentBookings.findAllByMeetingId("M-09")).thenReturn(List.of(booked, maintenance));
        meetingService.cancel("M-09", "OWNER", "Changed plans");
        assertEquals("CANCELLED", meeting.status); assertEquals("Changed plans", meeting.cancelReason);
        assertNotNull(meeting.cancelledAt); assertEquals("R-01", meeting.roomId);
        assertEquals(EquipmentBookingStatus.CANCELLED, booked.getStatus());
        assertEquals(EquipmentBookingStatus.MAINTENANCE, maintenance.getStatus());
        assertEquals(EquipmentBookingStatus.BOOKED, otherMeeting.getStatus());
    }

    @Test void rejectsForbiddenMissingRoomAndDuplicateCancellation() {
        assertStatus(HttpStatus.FORBIDDEN, () -> roomService.cancelRoomBooking("M-09", "OTHER"));
        assertStatus(HttpStatus.NOT_FOUND, () -> roomService.cancelRoomBooking("UNKNOWN", "OWNER"));
        meeting.roomId = null;
        assertStatus(HttpStatus.BAD_REQUEST, () -> roomService.cancelRoomBooking("M-09", "OWNER"));
        meeting.roomId = "R-01"; meeting.status = "CANCELLED";
        assertStatus(HttpStatus.CONFLICT, () -> meetingService.cancel("M-09", "OWNER", null));
    }

    @Test void failureBeforeMeetingSavePropagatesForTransactionRollback() {
        when(equipmentBookings.findAllByMeetingId("M-09")).thenReturn(List.of(booking(EquipmentBookingStatus.BOOKED)));
        doThrow(new IllegalStateException("database failure")).when(equipmentBookings).saveAll(anyList());
        assertThrows(IllegalStateException.class, () -> roomService.cancelRoomBooking("M-09", "OWNER"));
        verify(meetings, never()).saveAndFlush(any());
    }

    private static void assertStatus(HttpStatus expected, Runnable call) {
        assertEquals(expected, assertThrows(ResponseStatusException.class, call::run).getStatusCode());
    }
    private static Meeting meeting(String status) {
        var value = new Meeting(); value.meetingId = "M-09"; value.organizerId = "OWNER";
        value.roomId = "R-01"; value.status = status; value.startTime = LocalDateTime.now().plusDays(1);
        value.endTime = value.startTime.plusHours(1); return value;
    }
    private static EquipmentBooking booking(EquipmentBookingStatus status) {
        var value = new EquipmentBooking(); value.setStatus(status); return value;
    }
}
