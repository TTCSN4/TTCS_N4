package com.example.meetingmanagement.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.meetingmanagement.dto.MeetingResponse;
import com.example.meetingmanagement.dto.UpdateMeetingRequest;
import com.example.meetingmanagement.entity.MeetingStatus;
import com.example.meetingmanagement.exception.ApiExceptionHandler;
import com.example.meetingmanagement.exception.MeetingConflictException;
import com.example.meetingmanagement.exception.MeetingForbiddenException;
import com.example.meetingmanagement.exception.MeetingNotFoundException;
import com.example.meetingmanagement.service.MeetingService;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MeetingControllerTest {

    private StubMeetingService meetingService;

    private MockMvc mockMvc;
    private OffsetDateTime startTime;
    private OffsetDateTime endTime;

    @BeforeEach
    void setUp() {
        meetingService = new StubMeetingService();
        mockMvc = MockMvcBuilders.standaloneSetup(new MeetingController(meetingService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
        startTime = OffsetDateTime.now().plusDays(2);
        endTime = startTime.plusHours(1);
    }

    @Test
    void updateMeetingReturnsUpdatedMeeting() throws Exception {
        MeetingResponse response = new MeetingResponse(
                "M1", "Sprint review", null, startTime, endTime, "NV001",
                MeetingStatus.SCHEDULED, null, startTime.minusDays(1), startTime.minusHours(1));
        meetingService.updateResponse = response;

        mockMvc.perform(put("/api/meetings/1")
                        .header("X-User-Id", "NV001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("M1"))
                .andExpect(jsonPath("$.title").value("Sprint review"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
    }

    @Test
    void cancelMeetingReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/meetings/1").header("X-User-Id", "NV001"))
                .andExpect(status().isNoContent());
    }

    @Test
    void updateMeetingReturnsForbiddenForNonOrganizer() throws Exception {
        meetingService.updateException = new MeetingForbiddenException();

        mockMvc.perform(put("/api/meetings/1")
                        .header("X-User-Id", "NV099")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancelMeetingReturnsConflictForInvalidStatus() throws Exception {
        meetingService.cancelException =
                new MeetingConflictException("Only scheduled meetings can be modified or cancelled");

        mockMvc.perform(delete("/api/meetings/1").header("X-User-Id", "NV001"))
                .andExpect(status().isConflict());
    }

    @Test
    void cancelMeetingReturnsNotFoundForUnknownMeeting() throws Exception {
        meetingService.cancelException = new MeetingNotFoundException("M999");

        mockMvc.perform(delete("/api/meetings/999").header("X-User-Id", "NV001"))
                .andExpect(status().isNotFound());
    }

    private String updateBody() {
        return """
                {
                  "title": "Sprint review",
                  "description": "Updated meeting",
                  "startTime": "%s",
                  "endTime": "%s"
                }
                """.formatted(startTime, endTime);
    }

    private static final class StubMeetingService extends MeetingService {
        private MeetingResponse updateResponse;
        private RuntimeException updateException;
        private RuntimeException cancelException;

        private StubMeetingService() {
            super(null, Clock.systemUTC());
        }

        @Override
        public MeetingResponse updateMeeting(String meetingId, String actorId, UpdateMeetingRequest request) {
            if (updateException != null) {
                throw updateException;
            }
            return updateResponse;
        }

        @Override
        public void cancelMeeting(String meetingId, String actorId) {
            if (cancelException != null) {
                throw cancelException;
            }
        }
    }
}
