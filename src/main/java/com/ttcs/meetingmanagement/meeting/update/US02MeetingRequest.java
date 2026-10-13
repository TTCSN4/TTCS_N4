package com.ttcs.meetingmanagement.meeting.update;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

public record US02MeetingRequest(
    @NotBlank @Size(max = 255) String title, String description,
    @NotNull LocalDateTime startTime, @NotNull LocalDateTime endTime, String roomId) {}
