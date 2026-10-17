package com.ttcs.meetingmanagement.room;

import com.ttcs.meetingmanagement.dto.CreateRoomRequest;
import com.ttcs.meetingmanagement.dto.UpdateRoomRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    // GET - lay tat ca phong
    @GetMapping
    public ResponseEntity<List<Room>> getAllRooms() {
        return ResponseEntity.ok(
                roomService.getAllRooms()
        );
    }

    // US10 - loc phong theo so nguoi tham gia
    @GetMapping(params = "participants")
    public ResponseEntity<List<Room>> filterRoomsByParticipants(
            @RequestParam Integer participants) {

        return ResponseEntity.ok(
                roomService.filterByParticipantCount(participants)
        );
    }

    // GET - lay phong theo ID
    @GetMapping("/{id}")
    public ResponseEntity<Room> getRoomById(
            @PathVariable String id) {

        return ResponseEntity.ok(
                roomService.getRoomById(id)
        );
    }

    // US11.1 - POST: Them phong (Chi Admin)
    @PostMapping
    @PreAuthorize("@rbacAccess.isAdmin(authentication)")
    public ResponseEntity<Room> createRoom(
            @Valid @RequestBody CreateRoomRequest request) {

        Room room = roomService.createRoom(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(room);
    }

    // US11.2, US11.3, US11.4 - PUT: Cap nhat phong (Chi Admin)
    @PutMapping("/{id}")
    @PreAuthorize("@rbacAccess.isAdmin(authentication)")
    public ResponseEntity<Room> updateRoom(
            @PathVariable String id,
            @Valid @RequestBody UpdateRoomRequest request) {

        return ResponseEntity.ok(
                roomService.updateRoom(id, request)
        );
    }

    // DELETE: Xoa phong (Chi Admin)
    @DeleteMapping("/{id}")
    @PreAuthorize("@rbacAccess.isAdmin(authentication)")
    public ResponseEntity<Void> deleteRoom(
            @PathVariable String id) {

        roomService.deleteRoom(id);

        return ResponseEntity
                .noContent()
                .build();
    }
}