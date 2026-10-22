
package com.ttcs.meetingmanagement.service;

import com.ttcs.meetingmanagement.dto.*;
import com.ttcs.meetingmanagement.model.MeetingParticipant;
import com.ttcs.meetingmanagement.repository.MeetingParticipantRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MeetingParticipantServiceTest {

    @Mock
    private MeetingParticipantRepository repository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private MeetingParticipantService service;

    private void mockMeetingExists() {
        when(jdbcTemplate.queryForObject(
                contains("FROM meeting"),
                eq(Integer.class),
                eq("meeting-1")
        )).thenReturn(1);
    }

    private void mockUserExists() {
        when(jdbcTemplate.queryForObject(
                contains("FROM `user`"),
                eq(Integer.class),
                eq("user-1")
        )).thenReturn(1);
    }

    private MeetingParticipant sampleParticipant() {
        MeetingParticipant p = new MeetingParticipant();

        p.setParticipantId("participant-1");
        p.setMeetingId("meeting-1");
        p.setUserId("user-1");
        p.setStatus("PENDING");

        return p;
    }

    // TEST 1 - THEM NGUOI THAM DU
    @Test
    void addParticipant_success() {
        mockMeetingExists();
        mockUserExists();

        when(repository.save(any(MeetingParticipant.class)))
                .thenAnswer(i -> i.getArgument(0));

        MeetingParticipantResponse result =
                service.addParticipant(
                        "meeting-1",
                        new AddMeetingParticipantRequest("user-1")
                );

        assertEquals("PENDING", result.status());
        assertEquals("user-1", result.userId());
        assertNotNull(result.participantId());

        verify(repository).save(any(MeetingParticipant.class));
    }

    // TEST 2 - KHONG CHO THEM TRUNG
    @Test
    void addParticipant_duplicate() {
        mockMeetingExists();
        mockUserExists();

        when(repository.existsByMeetingIdAndUserId(
                "meeting-1", "user-1"
        )).thenReturn(true);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.addParticipant(
                        "meeting-1",
                        new AddMeetingParticipantRequest("user-1")
                )
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(repository, never()).save(any());
    }

    // TEST 3 - LAY DANH SACH
    @Test
    void getParticipants_success() {
        mockMeetingExists();

        when(repository.findByMeetingId("meeting-1"))
                .thenReturn(List.of(sampleParticipant()));

        List<MeetingParticipantResponse> result =
                service.getParticipants("meeting-1");

        assertEquals(1, result.size());
        assertEquals("user-1", result.get(0).userId());
    }

    // TEST 4 - CHAP NHAN
    @Test
    void acceptParticipant_success() {
        mockMeetingExists();

        MeetingParticipant p = sampleParticipant();

        when(repository.findByMeetingIdAndUserId(
                "meeting-1", "user-1"
        )).thenReturn(Optional.of(p));

        when(repository.save(any(MeetingParticipant.class)))
                .thenAnswer(i -> i.getArgument(0));

        MeetingParticipantResponse result =
                service.updateStatus(
                        "meeting-1",
                        "user-1",
                        new UpdateInvitationStatusRequest("ACCEPTED")
                );

        assertEquals("ACCEPTED", result.status());
        assertNotNull(result.respondedAt());
    }

    // TEST 5 - TU CHOI
    @Test
    void declineParticipant_success() {
        mockMeetingExists();

        MeetingParticipant p = sampleParticipant();

        when(repository.findByMeetingIdAndUserId(
                "meeting-1", "user-1"
        )).thenReturn(Optional.of(p));

        when(repository.save(any(MeetingParticipant.class)))
                .thenAnswer(i -> i.getArgument(0));

        MeetingParticipantResponse result =
                service.updateStatus(
                        "meeting-1",
                        "user-1",
                        new UpdateInvitationStatusRequest("DECLINED")
                );

        assertEquals("DECLINED", result.status());
    }

    // TEST 6 - XOA NGUOI THAM DU
    @Test
    void removeParticipant_success() {
        mockMeetingExists();

        MeetingParticipant p = sampleParticipant();

        when(repository.findByMeetingIdAndUserId(
                "meeting-1", "user-1"
        )).thenReturn(Optional.of(p));

        service.removeParticipant("meeting-1", "user-1");

        verify(repository).delete(p);
    }
}
