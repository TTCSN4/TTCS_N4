
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
}
