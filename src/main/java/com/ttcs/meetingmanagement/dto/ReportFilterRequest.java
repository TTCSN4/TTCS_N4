
package com.ttcs.meetingmanagement.dto;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

public record ReportFilterRequest(

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    LocalDate fromDate,

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    LocalDate toDate,

    String status,

    String roomId,

    String organizerId,

    String keyword

) {
}
