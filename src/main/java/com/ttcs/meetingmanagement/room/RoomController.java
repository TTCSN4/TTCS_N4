package com.ttcs.meetingmanagement.room;

import com.ttcs.meetingmanagement.dto.CreateRoomRequest;
import com.ttcs.meetingmanagement.dto.UpdateRoomRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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

    // GET - lấy tất cả phòng
    @GetMapping
    public ResponseEntity<List<Room>> getAllRooms() {
        return ResponseEntity.ok(
                roomService.getAllRooms()
        );
    }

    // US10 - lọc phòng theo số người tham gia
    @GetMapping(params = "participants")
    public ResponseEntity<List<Room>> filterRoomsByParticipants(
            @RequestParam Integer participants) {

        return ResponseEntity.ok(
                roomService.filterByParticipantCount(participants)
        );
    }

    // GET - lấy phòng theo ID
    @GetMapping("/{id}")
    public ResponseEntity<Room> getRoomById(
            @PathVariable String id) {

        return ResponseEntity.ok(
                roomService.getRoomById(id)
        );
    }

    // POST - thêm phòng
    @PostMapping
    public ResponseEntity<Room> createRoom(
            @Valid @RequestBody CreateRoomRequest request) {

        Room room = roomService.createRoom(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(room);
    }

    // PUT - cập nhật phòng
    @PutMapping("/{id}")
    public ResponseEntity<Room> updateRoom(
            @PathVariable String id,
            @Valid @RequestBody UpdateRoomRequest request) {

        return ResponseEntity.ok(
                roomService.updateRoom(id, request)
        );
    }

    // DELETE - xóa phòng
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRoom(
            @PathVariable String id) {

        roomService.deleteRoom(id);

        return ResponseEntity
                .noContent()
                .build();
    }
}