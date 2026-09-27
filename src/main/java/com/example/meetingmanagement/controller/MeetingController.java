package com.example.meetingmanagement.controller;

import com.example.meetingmanagement.dto.MeetingResponse;
import com.example.meetingmanagement.dto.UpdateMeetingRequest;
import com.example.meetingmanagement.service.MeetingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/meetings")
public class MeetingController {

    private final MeetingService meetingService;

    public MeetingController(MeetingService meetingService) {
        this.meetingService = meetingService;
    }

    @GetMapping("/{meetingId}")
    public MeetingResponse getMeeting(@PathVariable @Positive Long meetingId) {
        return meetingService.getMeeting(meetingId);
    }

    @GetMapping
    public List<MeetingResponse> getMeetings() {
        return meetingService.getMeetings();
    }

    @PutMapping("/{meetingId}")
    public MeetingResponse updateMeeting(
            @PathVariable @Positive Long meetingId,
            @RequestHeader("X-User-Id")
            @Pattern(regexp = "[A-Za-z]+[0-9]+", message = "must contain letters followed by a sequence number")
            String actorId,
            @Valid @RequestBody UpdateMeetingRequest request) {
        return meetingService.updateMeeting(meetingId, actorId, request);
    }

    @DeleteMapping("/{meetingId}")
    public ResponseEntity<Void> cancelMeeting(
            @PathVariable @Positive Long meetingId,
            @RequestHeader("X-User-Id")
            @Pattern(regexp = "[A-Za-z]+[0-9]+", message = "must contain letters followed by a sequence number")
            String actorId) {
        meetingService.cancelMeeting(meetingId, actorId);
        return ResponseEntity.noContent().build();
    }
}
