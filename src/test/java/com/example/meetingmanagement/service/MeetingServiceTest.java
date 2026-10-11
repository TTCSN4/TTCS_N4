package com.example.meetingmanagement.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import com.example.meetingmanagement.dto.UpdateMeetingRequest;
import com.example.meetingmanagement.entity.Meeting;
import com.example.meetingmanagement.entity.MeetingStatus;
import com.example.meetingmanagement.exception.MeetingConflictException;
import com.example.meetingmanagement.exception.MeetingForbiddenException;
import com.example.meetingmanagement.exception.MeetingNotFoundException;
import com.example.meetingmanagement.repository.MeetingRepository;
import java.time.OffsetDateTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MeetingServiceTest {

    @Mock
    private MeetingRepository meetingRepository;

    private MeetingService meetingService;
    private Meeting meeting;
    private static final Instant NOW = Instant.parse("2026-09-27T04:00:00Z");

    @BeforeEach
    void setUp() {
        meetingService = new MeetingService(meetingRepository, Clock.fixed(NOW, ZoneOffset.UTC));
        meeting = new Meeting();
        meeting.setId(1L);
        meeting.setOrganizerId("NV001");
        meeting.setStatus(MeetingStatus.SCHEDULED);
        meeting.setStartTime(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).plusHours(2));
        meeting.setEndTime(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).plusHours(3));
        lenient().when(meetingRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(meeting));
    }

    @Test
    void updateMeetingAllowsOrganizerAndPersistsNewValues() {
        UpdateMeetingRequest request = new UpdateMeetingRequest(
                "  Updated title  ", "New details", meeting.getStartTime().plusDays(1), meeting.getEndTime().plusDays(1));

        var response = meetingService.updateMeeting(1L, "NV001", request);

        assertEquals("Updated title", response.title());
        assertEquals("New details", response.description());
        verify(meetingRepository).findByIdForUpdate(1L);
        verify(meetingRepository).saveAndFlush(meeting);
    }

    @Test
    void updateMeetingRejectsNonOrganizer() {
        UpdateMeetingRequest request = new UpdateMeetingRequest(
                "Updated title", null, meeting.getStartTime().plusDays(1), meeting.getEndTime().plusDays(1));

        assertThrows(MeetingForbiddenException.class, () -> meetingService.updateMeeting(1L, "NV099", request));
    }

    @Test
    void cancelMeetingRejectsMeetingThatIsNotScheduled() {
        meeting.setStatus(MeetingStatus.CANCELLED);

        assertThrows(MeetingConflictException.class, () -> meetingService.cancelMeeting(1L, "NV001"));
    }

    @Test
    void cancelMeetingMarksScheduledMeetingAsCancelled() {
        meetingService.cancelMeeting(1L, "NV001");

        assertEquals(MeetingStatus.CANCELLED, meeting.getStatus());
        verify(meetingRepository).saveAndFlush(meeting);
    }

    @Test
    void cancelMeetingRejectsMeetingThatHasAlreadyStarted() {
        meeting.setStartTime(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).minusMinutes(1));

        assertThrows(MeetingConflictException.class, () -> meetingService.cancelMeeting(1L, "NV001"));
    }

    @Test
    void updateMeetingRejectsMeetingThatHasAlreadyStarted() {
        meeting.setStartTime(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
        UpdateMeetingRequest request = new UpdateMeetingRequest(
                "Updated title", null, meeting.getStartTime().plusDays(1), meeting.getEndTime().plusDays(1));

        assertThrows(MeetingConflictException.class, () -> meetingService.updateMeeting(1L, "NV001", request));
    }

    @Test
    void updateMeetingRejectsCompletedMeeting() {
        meeting.setStatus(MeetingStatus.COMPLETED);
        UpdateMeetingRequest request = new UpdateMeetingRequest(
                "Updated title", null, meeting.getStartTime().plusDays(1), meeting.getEndTime().plusDays(1));

        assertThrows(MeetingConflictException.class, () -> meetingService.updateMeeting(1L, "NV001", request));
    }

    @Test
    void cancelMeetingRejectsNonOrganizer() {
        assertThrows(MeetingForbiddenException.class, () -> meetingService.cancelMeeting(1L, "NV099"));
    }

    @Test
    void getMeetingRejectsUnknownId() {
        when(meetingRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(MeetingNotFoundException.class, () -> meetingService.getMeeting(99L));
    }
}
