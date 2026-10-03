package com.ttcs.meetingmanagement.controller;

import com.ttcs.meetingmanagement.dto.CreateMeetingRequest;
import com.ttcs.meetingmanagement.dto.MeetingResponse;
import com.ttcs.meetingmanagement.dto.UpdateMeetingRequest;
import com.ttcs.meetingmanagement.exception.UnauthenticatedException;
import com.ttcs.meetingmanagement.model.Meeting;
import com.ttcs.meetingmanagement.service.MeetingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/meetings")
@Validated
public class MeetingController {

    private final MeetingService meetingService;

    public MeetingController(MeetingService meetingService) {
        this.meetingService = meetingService;
    }

    @PostMapping
    public ResponseEntity<MeetingResponse> createMeeting(
            @Valid @RequestBody CreateMeetingRequest request
    ) {
        Meeting meeting = meetingService.createMeeting(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(MeetingResponse.from(meeting));
    }

    @GetMapping
    public ResponseEntity<List<MeetingResponse>> getMeetings() {
        return ResponseEntity.ok(meetingService.getMeetings());
    }

    @GetMapping("/{meetingId}")
    public ResponseEntity<MeetingResponse> getMeeting(
            @PathVariable @Positive Long meetingId
    ) {
        return ResponseEntity.ok(meetingService.getMeeting(meetingId));
    }

    @PutMapping("/{meetingId}")
    public ResponseEntity<MeetingResponse> updateMeeting(
            @PathVariable @Positive Long meetingId,
            @RequestHeader(value = "X-User-Id", required = false)
            @Pattern(regexp = "[A-Za-z]+[0-9]+", message = "must contain letters followed by a sequence number")
            String actorId,
            @Valid @RequestBody UpdateMeetingRequest request
    ) {
        if (actorId == null || actorId.isBlank()) {
            throw new UnauthenticatedException();
        }
        return ResponseEntity.ok(meetingService.updateMeeting(meetingId, actorId, request));
    }

    @DeleteMapping("/{meetingId}")
    public ResponseEntity<Void> cancelMeeting(
            @PathVariable @Positive Long meetingId,
            @RequestHeader(value = "X-User-Id", required = false)
            @Pattern(regexp = "[A-Za-z]+[0-9]+", message = "must contain letters followed by a sequence number")
            String actorId
    ) {
        if (actorId == null || actorId.isBlank()) {
            throw new UnauthenticatedException();
        }
        meetingService.cancelMeeting(meetingId, actorId);
        return ResponseEntity.noContent().build();
    }
}