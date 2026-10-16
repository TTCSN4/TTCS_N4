package com.ttcs.meetingmanagement.meeting.availability;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;

public final class US05AvailabilityDtos {
    private US05AvailabilityDtos() {}
    public record ParticipantResponse(String id, String name, String role) {}
    public record AvailabilityRequest(@NotNull LocalDate date, @NotEmpty List<@NotBlank String> participantIds,
            @Min(15) @Max(480) Integer durationMinutes) {}
    public record SlotResponse(String start, String end, int durationMinutes) {}
    public record AvailabilityResponse(LocalDate date, List<SlotResponse> slots, int count) {}
}
