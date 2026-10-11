package com.ttcs.meetingmanagement.meeting.availability;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;
public final class AvailabilityDtos {
    private AvailabilityDtos() {}
    public record ParticipantResponse(Long id, String name, String role) {}
    public record AvailabilityRequest(@NotNull LocalDate date, @NotEmpty List<Long> participantIds, @Positive Integer durationMinutes) {}
    public record SlotResponse(String start, String end, int durationMinutes) {}
    public record AvailabilityResponse(LocalDate date, List<SlotResponse> slots, int count) {}
}
