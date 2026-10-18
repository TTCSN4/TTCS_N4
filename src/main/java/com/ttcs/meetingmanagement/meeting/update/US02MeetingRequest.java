package com.ttcs.meetingmanagement.meeting.update;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;
import com.ttcs.meetingmanagement.equipment.EquipmentReservationService.Item;
public record US02MeetingRequest(@NotBlank @Size(max=255) String title,String description,
    @NotNull LocalDateTime startTime,@NotNull LocalDateTime endTime,String roomId,
    List<String> participantIds,@Valid List<Item> equipment,String recurrenceRule,Scope scope) {
    public enum Scope { INSTANCE, SERIES }
    public US02MeetingRequest(String title,String description,LocalDateTime start,LocalDateTime end,String room){this(title,description,start,end,room,null,null,null,Scope.INSTANCE);}
}
