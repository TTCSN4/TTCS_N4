package com.example.demo.meeting;

import com.example.demo.meeting.dto.UpdateMeetingRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class MeetingUpdateIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Long testMeetingId;

    @BeforeEach
    void setUp() {
        Meeting meeting = new Meeting();
        meeting.setTitle("Cuoc hop ban dau");
        meeting.setDescription("Noi dung ban dau");
        meeting.setOrganizerId("user01");
        meeting.setStatus(MeetingStatus.SCHEDULED);
        meeting.setStartTime(OffsetDateTime.now(ZoneOffset.UTC).plusDays(2));
        meeting.setEndTime(OffsetDateTime.now(ZoneOffset.UTC).plusDays(2).plusHours(1));
        meeting = meetingRepository.save(meeting);
        testMeetingId = meeting.getId();
    }

    @Test
    void testMeetingStatusIncludesInProgress() {
        assertTrue(Arrays.asList(MeetingStatus.values()).contains(MeetingStatus.IN_PROGRESS));
    }

    @Test
    void testUpdateMeeting_Success() throws Exception {
        UpdateMeetingRequest request = new UpdateMeetingRequest(
                "Cuoc hop da duoc sua",
                "Noi dung sau khi chinh sua",
                OffsetDateTime.now(ZoneOffset.UTC).plusDays(3),
                OffsetDateTime.now(ZoneOffset.UTC).plusDays(3).plusHours(2)
        );

        mockMvc.perform(put("/api/meetings/" + testMeetingId)
                        .header("X-User-Id", "user01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(testMeetingId.intValue())))
                .andExpect(jsonPath("$.title", is("Cuoc hop da duoc sua")))
                .andExpect(jsonPath("$.description", is("Noi dung sau khi chinh sua")))
                .andExpect(jsonPath("$.status", is("SCHEDULED")));
    }

    @Test
    void testUpdateMeeting_ForbiddenWhenNotOrganizer() throws Exception {
        UpdateMeetingRequest request = new UpdateMeetingRequest(
                "Sua tieu de",
                "Mota",
                OffsetDateTime.now(ZoneOffset.UTC).plusDays(3),
                OffsetDateTime.now(ZoneOffset.UTC).plusDays(3).plusHours(1)
        );

        mockMvc.perform(put("/api/meetings/" + testMeetingId)
                        .header("X-User-Id", "user02")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("Only the meeting organizer can modify or cancel this meeting")));
    }

    @Test
    void testCancelMeeting_Success() throws Exception {
        mockMvc.perform(delete("/api/meetings/" + testMeetingId)
                        .header("X-User-Id", "user01"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/meetings/" + testMeetingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CANCELLED")));
    }

    @Test
    void testCancelMeeting_AlreadyCancelled_ThrowsConflict() throws Exception {
        // First cancel
        mockMvc.perform(delete("/api/meetings/" + testMeetingId)
                        .header("X-User-Id", "user01"))
                .andExpect(status().isNoContent());

        // Cancel again
        mockMvc.perform(delete("/api/meetings/" + testMeetingId)
                        .header("X-User-Id", "user01"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Only scheduled meetings can be modified or cancelled")));
    }

    @Test
    void testUpdateMeeting_AlreadyCancelled_ThrowsConflict() throws Exception {
        // Cancel first
        mockMvc.perform(delete("/api/meetings/" + testMeetingId)
                        .header("X-User-Id", "user01"))
                .andExpect(status().isNoContent());

        UpdateMeetingRequest request = new UpdateMeetingRequest(
                "Sua sau khi huy",
                "Mota",
                OffsetDateTime.now(ZoneOffset.UTC).plusDays(3),
                OffsetDateTime.now(ZoneOffset.UTC).plusDays(3).plusHours(1)
        );

        mockMvc.perform(put("/api/meetings/" + testMeetingId)
                        .header("X-User-Id", "user01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Only scheduled meetings can be modified or cancelled")));
    }

    @Test
    void testUpdateMeeting_MissingHeader_ThrowsUnauthorized() throws Exception {
        UpdateMeetingRequest request = new UpdateMeetingRequest(
                "Sua",
                "Mota",
                OffsetDateTime.now(ZoneOffset.UTC).plusDays(3),
                OffsetDateTime.now(ZoneOffset.UTC).plusDays(3).plusHours(1)
        );

        mockMvc.perform(put("/api/meetings/" + testMeetingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
}
