package com.ttcs.meetingmanagement.roombooking;

import com.ttcs.meetingmanagement.dto.RoomBookingRequest;
import com.ttcs.meetingmanagement.dto.RoomBookingResponse;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
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

    @PostMapping
    public ResponseEntity<RoomBookingResponse> bookRoom(
            @Valid @RequestBody RoomBookingRequest request) {

        RoomBookingResponse response =
                roomBookingService.bookRoom(request);

        return ResponseEntity.ok(response);
    }
}