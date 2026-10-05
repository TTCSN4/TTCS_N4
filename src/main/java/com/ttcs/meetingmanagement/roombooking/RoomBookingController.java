package com.ttcs.meetingmanagement.roombooking;

import com.ttcs.meetingmanagement.dto.RoomBookingRequest;
import com.ttcs.meetingmanagement.dto.RoomBookingResponse;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/room-bookings")
public class RoomBookingController {

    private final RoomBookingService roomBookingService;

    public RoomBookingController(
            RoomBookingService roomBookingService) {

        this.roomBookingService = roomBookingService;
    }

    // US08 - Đặt phòng
    @PostMapping
    public ResponseEntity<RoomBookingResponse> bookRoom(
            @Valid @RequestBody RoomBookingRequest request) {

        RoomBookingResponse response =
                roomBookingService.bookRoom(request);

        return ResponseEntity.ok(response);
    }

    // US09 - Hủy đặt phòng
    @DeleteMapping("/{meetingId}")
    public ResponseEntity<RoomBookingResponse> cancelRoomBooking(
            @PathVariable Long meetingId) {

        RoomBookingResponse response =
                roomBookingService.cancelRoomBooking(meetingId);

        return ResponseEntity.ok(response);
    }
}