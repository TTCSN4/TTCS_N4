
package com.ttcs.meetingmanagement.report.cancellation;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/reports")
@PreAuthorize("@rbacAccess.isAdmin(authentication)")
public class MeetingCancellationController {

    private final MeetingCancellationService service;

    public MeetingCancellationController(
            MeetingCancellationService service
    ) {
        this.service = service;
    }

    @GetMapping("/meeting-cancellations")
    public MeetingCancellationResponse getReport(

            @RequestParam("from")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,

            @RequestParam("to")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to,

            @RequestParam(
                    value = "roomId",
                    required = false
            )
            String roomId,

            @RequestParam(
                    value = "organizerId",
                    required = false
            )
            String organizerId
    ) {

        return service.generateReport(
                from,
                to,
                roomId,
                organizerId
        );
    }
}
