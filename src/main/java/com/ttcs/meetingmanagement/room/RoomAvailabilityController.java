
package com.ttcs.meetingmanagement.room;

import com.ttcs.meetingmanagement.dto.AvailableRoomResponse;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/rooms")
public class RoomAvailabilityController {

    private final RoomAvailabilityService availabilityService;

    public RoomAvailabilityController(
            RoomAvailabilityService availabilityService) {

        this.availabilityService = availabilityService;
    }

    @GetMapping("/available")
    public List<AvailableRoomResponse> getAvailableRooms(

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime startTime,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime endTime,

            @RequestParam(required = false)
            Integer minCapacity) {

        return availabilityService.getAvailableRooms(
                startTime,
                endTime,
                minCapacity
        );
    }
}
