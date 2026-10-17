package com.example.meetingmanagement.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;

public record UpdateMeetingRequest(
        @NotBlank @Size(max = 255) String title,
        @Size(max = 5000) String description,
        @NotNull @Future OffsetDateTime startTime,
        @NotNull @Future OffsetDateTime endTime,
        String room,
        List<String> participantIds,
        List<EquipmentRequest> equipment,
        String recurrenceRule,
        UpdateScope scope) {

    public UpdateMeetingRequest(String title,String description,OffsetDateTime startTime,OffsetDateTime endTime){
        this(title,description,startTime,endTime,null,null,null,null,UpdateScope.INSTANCE);
    }
    public record EquipmentRequest(@NotBlank String type,@Min(1) int quantity){}
    public enum UpdateScope { INSTANCE, SERIES }

    @AssertTrue(message = "endTime must be after startTime")
    public boolean isTimeRangeValid() {
        return startTime == null || endTime == null || endTime.isAfter(startTime);
    }
}
