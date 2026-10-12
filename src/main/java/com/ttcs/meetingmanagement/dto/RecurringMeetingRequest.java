package com.ttcs.meetingmanagement.dto;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.List;
import com.ttcs.meetingmanagement.recurrence.RecurrenceFrequency;
public record RecurringMeetingRequest(
 Long organizerId,
 @NotBlank String roomId,
 @NotBlank @Size(min=3,max=120) String title,
 @Size(max=2000) String description,
 @NotNull OffsetDateTime startTime,@NotNull OffsetDateTime endTime,
 @NotNull RecurrenceFrequency frequency,
 @Min(1) @Max(52) Integer interval,
 @Min(1) @Max(100) Integer occurrences,
 List<@Min(1) @Max(7) Integer> weekdays,
 @Min(1) @Max(31) Integer monthDay,
 LocalDate until,
 List<@Email @NotBlank String> participants
){}
