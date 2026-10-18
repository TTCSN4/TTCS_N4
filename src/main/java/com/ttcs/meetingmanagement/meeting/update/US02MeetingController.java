package com.ttcs.meetingmanagement.meeting.update;

import com.ttcs.meetingmanagement.model.Meeting;
import com.ttcs.meetingmanagement.security.BackendActor;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/meetings")
public class US02MeetingController {
    private final US02MeetingService service;
    public US02MeetingController(US02MeetingService service) { this.service = service; }
    @GetMapping("/{id}") public Meeting get(@PathVariable String id) { return service.get(id); }
    @PutMapping("/{id}") public Meeting update(@PathVariable String id, @Valid @RequestBody US02MeetingRequest request) {
        return service.update(id, BackendActor.requireUserId(), request);
    }
    @DeleteMapping("/{id}") public ResponseEntity<Void> cancel(@PathVariable String id, @RequestParam(required = false) String reason) {
        service.cancel(id, BackendActor.requireUserId(), reason); return ResponseEntity.noContent().build();
    }
}
