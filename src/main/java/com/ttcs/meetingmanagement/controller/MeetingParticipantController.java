
package com.ttcs.meetingmanagement.controller;

import com.ttcs.meetingmanagement.dto.AddMeetingParticipantRequest;
import com.ttcs.meetingmanagement.dto.UpdateInvitationStatusRequest;
import com.ttcs.meetingmanagement.dto.MeetingParticipantResponse;
import com.ttcs.meetingmanagement.service.MeetingParticipantService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/meetings/{meetingId}/participants")
public class MeetingParticipantController {

    private final MeetingParticipantService service;

    public MeetingParticipantController(
            MeetingParticipantService service
    ) {
        this.service = service;
    }

    // POST: MOI NGUOI THAM DU
    @PostMapping
    public ResponseEntity<MeetingParticipantResponse> addParticipant(
            @PathVariable String meetingId,
            @RequestBody AddMeetingParticipantRequest request
    ) {

        MeetingParticipantResponse result =
                service.addParticipant(meetingId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(result);
    }

    // GET: DANH SACH NGUOI THAM DU
    @GetMapping
    public ResponseEntity<List<MeetingParticipantResponse>> getParticipants(
            @PathVariable String meetingId
    ) {

        return ResponseEntity.ok(
                service.getParticipants(meetingId)
        );
    }

    // DELETE: XOA NGUOI THAM DU
    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> removeParticipant(
            @PathVariable String meetingId,
            @PathVariable String userId
    ) {

        service.removeParticipant(meetingId, userId);

        return ResponseEntity.noContent().build();
    }

    // PATCH: PHAN HOI LOI MOI
    @PatchMapping("/{userId}/status")
    public ResponseEntity<MeetingParticipantResponse> updateStatus(
            @PathVariable String meetingId,
            @PathVariable String userId,
            @RequestBody UpdateInvitationStatusRequest request
    ) {

        return ResponseEntity.ok(
                service.updateStatus(meetingId, userId, request)
        );
    }
}
