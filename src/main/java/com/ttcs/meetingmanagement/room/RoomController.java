package com.ttcs.meetingmanagement.room;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<RoomResponse> getRoomById(
            @PathVariable Long id
    ) {

        RoomResponse room = roomService.getRoomById(id);

        return ResponseEntity.ok(room);
    }

    @GetMapping(params = "participants")
    public ResponseEntity<List<RoomResponse>> filterRoomsByParticipants(
            @RequestParam Integer participants
    ) {

        List<RoomResponse> rooms =
                roomService.filterByParticipantCount(participants);

        return ResponseEntity.ok(rooms);
    }
}