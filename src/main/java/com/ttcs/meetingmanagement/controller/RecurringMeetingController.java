<<<<<<< HEAD

package com.ttcs.meetingmanagement.controller;

import com.ttcs.meetingmanagement.dto.RecurringMeetingRequest;
import com.ttcs.meetingmanagement.dto.RecurringMeetingResponse;
import com.ttcs.meetingmanagement.recurrence.RecurringMeetingService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/meetings")
public class RecurringMeetingController {

    private final RecurringMeetingService recurringMeetingService;

    public RecurringMeetingController(
            RecurringMeetingService recurringMeetingService) {

        this.recurringMeetingService = recurringMeetingService;
    }

    @PostMapping("/recurring")
    public ResponseEntity<RecurringMeetingResponse> createRecurring(
            @RequestBody RecurringMeetingRequest request) {

        RecurringMeetingResponse response =
                recurringMeetingService.create(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
=======
package com.ttcs.meetingmanagement.controller;
import com.ttcs.meetingmanagement.dto.*;
import com.ttcs.meetingmanagement.recurrence.RecurringMeetingService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
@RestController @RequestMapping("/api/meetings")
public class RecurringMeetingController {
 private final RecurringMeetingService service;
 public RecurringMeetingController(RecurringMeetingService service){this.service=service;}
 @PostMapping("/recurring") public ResponseEntity<RecurringMeetingResponse> create(@Valid @RequestBody RecurringMeetingRequest r){return ResponseEntity.status(201).body(service.create(r));}
 @ExceptionHandler(ResponseStatusException.class) public ResponseEntity<Map<String,String>> invalid(ResponseStatusException e){return ResponseEntity.status(e.getStatusCode()).body(Map.of("message",e.getReason()==null?"Invalid request":e.getReason()));}
>>>>>>> parent of 64bacae (chore(US03): remove backend files and preserve directories)
}
