
package com.ttcs.meetingmanagement.report;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/reports")
@PreAuthorize("@rbacAccess.isAdmin(authentication)")
public class RoomUsageController {

    private final RoomUsageService roomUsageService;

    public RoomUsageController(
            RoomUsageService roomUsageService
    ) {
        this.roomUsageService = roomUsageService;
    }

    @GetMapping("/room-usage")
    public RoomUsageResponse getRoomUsage(

            @RequestParam("from")
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE
            )
            LocalDate from,

            @RequestParam("to")
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE
            )
            LocalDate to,

            @RequestParam(
                    value = "roomId",
                    required = false
            )
            String roomId
    ) {

        return roomUsageService.generateReport(
                from,
                to,
                roomId
        );
    }
}
