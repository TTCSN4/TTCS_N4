package com.ttcs.meetingmanagement.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public record UpdateMeetingRequest(
        @NotBlank @Size(max = 255) String title,
        @Size(max = 5000) String description,
        @NotNull @Future OffsetDateTime startTime,
        @NotNull @Future OffsetDateTime endTime) {

    @AssertTrue(message = "endTime must be after startTime")
    public boolean isTimeRangeValid() {
        return startTime == null || endTime == null || endTime.isAfter(startTime);
    }
}
