package com.ttcs.meetingmanagement.meeting.availability;

import com.ttcs.meetingmanagement.meeting.Meeting;
import com.ttcs.meetingmanagement.meeting.MeetingRepository;
import com.ttcs.meetingmanagement.meeting.participant.Participant;
import com.ttcs.meetingmanagement.meeting.participant.ParticipantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static com.ttcs.meetingmanagement.meeting.availability.AvailabilityDtos.AvailabilityRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AvailabilityServiceTest {
    private ParticipantRepository participantRepository;
    private MeetingRepository meetingRepository;
    private AvailabilityService service;

    @BeforeEach
    void setUp() {
        participantRepository = mock(ParticipantRepository.class);
        meetingRepository = mock(MeetingRepository.class);
        service = new AvailabilityService(participantRepository, meetingRepository);
        when(participantRepository.findAllById(any())).thenReturn(List.of(
                new Participant("Long", "Kỹ thuật"),
                new Participant("My", "Sản phẩm")
        ));
    }

    @Test
    void returnsSlotsWhenAllSelectedParticipantsAreFree() {
        when(meetingRepository.findDistinctByParticipants_IdInAndStartTimeLessThanAndEndTimeGreaterThan(any(), any(), any()))
                .thenReturn(List.of());

        var response = service.find(new AvailabilityRequest(LocalDate.of(2026, 9, 26), List.of(1L, 2L), 60));

        assertThat(response.count()).isEqualTo(9);
        assertThat(response.slots()).extracting(slot -> slot.start() + "-" + slot.end())
                .containsExactly("08:00-09:00", "09:00-10:00", "10:00-11:00", "11:00-12:00",
                        "12:00-13:00", "13:00-14:00", "14:00-15:00", "15:00-16:00", "16:00-17:00");
    }

    @Test
    void mergesOverlappingMeetingsAcrossParticipants() {
        when(meetingRepository.findDistinctByParticipants_IdInAndStartTimeLessThanAndEndTimeGreaterThan(any(), any(), any()))
                .thenReturn(List.of(meeting(9, 0, 10, 30), meeting(10, 0, 11, 0), meeting(13, 0, 14, 0)));

        var response = service.find(new AvailabilityRequest(LocalDate.of(2026, 9, 26), List.of(1L, 2L), 60));

        assertThat(response.slots()).extracting(slot -> slot.start() + "-" + slot.end())
                .containsExactly("08:00-09:00", "11:00-12:00", "12:00-13:00", "14:00-15:00",
                        "15:00-16:00", "16:00-17:00");
    }

    @Test
    void clipsMeetingsThatCrossWorkingHours() {
        when(meetingRepository.findDistinctByParticipants_IdInAndStartTimeLessThanAndEndTimeGreaterThan(any(), any(), any()))
                .thenReturn(List.of(meeting(7, 0, 9, 0), meeting(16, 30, 18, 0)));

        var response = service.find(new AvailabilityRequest(LocalDate.of(2026, 9, 26), List.of(1L, 2L), 30));

        assertThat(response.slots()).extracting(slot -> slot.start() + "-" + slot.end())
                .startsWith("09:00-09:30").endsWith("16:00-16:30");
    }

    @Test
    void rejectsUnknownParticipantsAndInvalidDuration() {
        when(participantRepository.findAllById(List.of(1L, 99L)))
                .thenReturn(List.of(new Participant("Long", "Kỹ thuật")));

        assertThatThrownBy(() -> service.find(new AvailabilityRequest(LocalDate.of(2026, 9, 26), List.of(1L, 99L), 60)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Người tham dự không hợp lệ.");
        assertThatThrownBy(() -> service.find(new AvailabilityRequest(LocalDate.of(2026, 9, 26), List.of(1L, 2L), 10)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Thời lượng phải từ 15 đến 480 phút.");
    }

    private Meeting meeting(int startHour, int startMinute, int endHour, int endMinute) {
        LocalDate date = LocalDate.of(2026, 9, 26);
        LocalDateTime start = date.atTime(startHour, startMinute);
        LocalDateTime end = date.atTime(endHour, endMinute);
        return new Meeting("Busy", start, end);
    }
}
