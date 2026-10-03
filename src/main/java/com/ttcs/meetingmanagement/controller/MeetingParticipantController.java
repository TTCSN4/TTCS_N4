package com.ttcs.meetingmanagement.controller;

import com.ttcs.meetingmanagement.model.MeetingParticipant;
import com.ttcs.meetingmanagement.service.MeetingParticipantService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/meetings/{meetingId}/participants")
public class MeetingParticipantController {

    private final MeetingParticipantService participantService;

    public MeetingParticipantController(MeetingParticipantService participantService) {
        this.participantService = participantService;
    }

    @GetMapping
    public ResponseEntity<List<MeetingParticipant>> getParticipants(
            @PathVariable Long meetingId
    ) {
        return ResponseEntity.ok(participantService.getParticipants(meetingId));
    }

    @PostMapping
    public ResponseEntity<MeetingParticipant> addParticipant(
            @PathVariable Long meetingId,
            @Valid @RequestBody ParticipantRequest request
    ) {
        MeetingParticipant participant = participantService.addParticipant(
            meetingId,
            request.name(),
            request.email(),
            request.role(),
            request.status(),
            request.notes()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(participant);
    }

        @PutMapping("/{participantId}")
        public ResponseEntity<MeetingParticipant> updateParticipant(
            @PathVariable Long meetingId,
            @PathVariable Long participantId,
            @Valid @RequestBody ParticipantRequest request
        ) {
        return ResponseEntity.ok(participantService.updateParticipant(
            meetingId,
            participantId,
            request.name(),
            request.email(),
            request.role(),
            request.status(),
            request.notes()
        ));
        }

    @DeleteMapping("/{participantId}")
    public ResponseEntity<Void> removeParticipant(
            @PathVariable Long meetingId,
            @PathVariable Long participantId
    ) {
        participantService.removeParticipant(meetingId, participantId);
        return ResponseEntity.noContent().build();
    }

    public record ParticipantRequest(
            @NotBlank(message = "TÃªn ngÆ°á»i tham dá»± khÃ´ng Ä‘Æ°á»£c Ä‘á»ƒ trá»‘ng") String name,
            @Email(message = "Email khÃ´ng há»£p lá»‡") String email,
            String role,
            String status,
            String notes
    ) {
    }
}