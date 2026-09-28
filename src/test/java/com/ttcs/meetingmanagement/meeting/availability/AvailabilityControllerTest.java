package com.ttcs.meetingmanagement.meeting.availability;

import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static com.ttcs.meetingmanagement.meeting.availability.AvailabilityDtos.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class AvailabilityControllerTest {
    private AvailabilityService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(AvailabilityService.class);
        var mapper = JsonMapper.builder().findAndAddModules().build();
        mvc = standaloneSetup(new AvailabilityController(service))
                .setMessageConverters(new JacksonJsonHttpMessageConverter(mapper))
                .build();
    }

    @Test
    void exposesParticipants() throws Exception {
        when(service.participants()).thenReturn(List.of(new ParticipantResponse(1L, "Long", "Kỹ thuật")));

        mvc.perform(get("/api/participants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participants[0].id").value(1))
                .andExpect(jsonPath("$.participants[0].name").value("Long"));
    }

    @Test
    void returnsSuitableSlots() throws Exception {
        when(service.find(any())).thenReturn(new AvailabilityResponse(
                LocalDate.of(2026, 9, 26), List.of(new SlotResponse("09:00", "10:00", 60)), 1));

        mvc.perform(post("/api/availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"date":"2026-09-26","participantIds":[1,2],"durationMinutes":60}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-09-26"))
                .andExpect(jsonPath("$.slots[0].start").value("09:00"))
                .andExpect(jsonPath("$.slots[0].end").value("10:00"))
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void rejectsAnEmptyParticipantList() throws Exception {
        mvc.perform(post("/api/availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"date":"2026-09-26","participantIds":[],"durationMinutes":60}
                                """))
                .andExpect(status().isBadRequest());
    }
}
