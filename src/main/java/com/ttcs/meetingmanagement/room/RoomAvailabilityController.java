package com.ttcs.meetingmanagement.room;

import com.ttcs.meetingmanagement.dto.AvailableRoomResponse;
import com.ttcs.meetingmanagement.dto.RoomAvailabilitySummaryResponse;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.List;

@RestController
@RequestMapping("/api/rooms")
@PreAuthorize("@rbacAccess.canAccessAvailableRooms(authentication)")
public class RoomAvailabilityController {

    private final RoomAvailabilityService availabilityService;

    public RoomAvailabilityController(
            RoomAvailabilityService availabilityService) {

        this.availabilityService = availabilityService;
    }

    // US07: Tra cứu danh sách các phòng còn trống
    @GetMapping("/available")
    public List<AvailableRoomResponse> getAvailableRooms(
            @RequestParam String startTime,
            @RequestParam String endTime,
            @RequestParam(required = false) Integer minCapacity) {

        return availabilityService.getAvailableRooms(
                parseDateTime(startTime),
                parseDateTime(endTime),
                minCapacity
        );
    }

    // US07 mở rộng: Trả về trạng thái thực tế toàn bộ phòng (trống, trùng lịch, bảo trì, ngừng sử dụng)
    @GetMapping("/availability")
    public RoomAvailabilitySummaryResponse getRoomAvailabilityOverview(
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false) Integer minCapacity) {

        return availabilityService.getRoomAvailabilityOverview(
                parseDateTime(startTime),
                parseDateTime(endTime),
                minCapacity
        );
    }

    private OffsetDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (Exception ex) {
            return LocalDateTime.parse(value).atZone(ZoneId.systemDefault()).toOffsetDateTime();
        }
    }
}
