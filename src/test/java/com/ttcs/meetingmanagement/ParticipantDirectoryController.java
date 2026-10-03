package com.example.demo.meeting;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/participants")
public class ParticipantDirectoryController {

    private final MeetingParticipantService participantService;

    public ParticipantDirectoryController(MeetingParticipantService participantService) {
        this.participantService = participantService;
    }

    @GetMapping
    public ResponseEntity<List<MeetingParticipant>> getAllParticipants() {
        return ResponseEntity.ok(participantService.getAllParticipants());
    }
}