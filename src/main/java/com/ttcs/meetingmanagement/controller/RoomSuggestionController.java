
package com.ttcs.meetingmanagement.controller;

import com.ttcs.meetingmanagement.dto.RoomSuggestionRequest;
import com.ttcs.meetingmanagement.dto.RoomSuggestionResponse;
import com.ttcs.meetingmanagement.service.RoomSuggestionService;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rooms")
public class RoomSuggestionController {

    private final RoomSuggestionService roomSuggestionService;

    public RoomSuggestionController(
            RoomSuggestionService roomSuggestionService) {
        this.roomSuggestionService = roomSuggestionService;
    }

    // US26 - Goi y phong hop
    @PostMapping("/suggestions")
    public ResponseEntity<List<RoomSuggestionResponse>> suggestRooms(
            @Valid @RequestBody RoomSuggestionRequest request) {

        return ResponseEntity.ok(
                roomSuggestionService.suggestRooms(request)
        );
    }
}
