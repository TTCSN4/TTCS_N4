
package com.ttcs.meetingmanagement.roombooking;

import com.ttcs.meetingmanagement.dto.RoomBookingRequest;
import com.ttcs.meetingmanagement.dto.RoomBookingResponse;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/room-bookings")
public class RoomBookingController {

    private final RoomBookingService roomBookingService;

    public RoomBookingController(
            RoomBookingService roomBookingService) {

        this.roomBookingService = roomBookingService;
    }

    // US08 + US21: Dat phong va kiem tra quyen
    @PostMapping
    public ResponseEntity<RoomBookingResponse> bookRoom(
            @Valid @RequestBody RoomBookingRequest request) {

        RoomBookingResponse response =
                roomBookingService.bookRoom(request);

        return ResponseEntity.ok(response);
    }

    // US09: Huy dat phong
    @DeleteMapping("/{meetingId}")
    public ResponseEntity<RoomBookingResponse> cancelRoomBooking(
            @PathVariable String meetingId) {

        RoomBookingResponse response =
                roomBookingService.cancelRoomBooking(meetingId);

        return ResponseEntity.ok(response);
    }
}
