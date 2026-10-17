package com.example.meetingmanagement.dto;

import com.example.meetingmanagement.entity.Meeting;
import com.example.meetingmanagement.entity.MeetingStatus;
import java.time.OffsetDateTime;
import java.util.*;
import com.example.meetingmanagement.entity.MeetingEquipment;

public record MeetingResponse(
        Long id,
        String title,
        String description,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        String organizerId,
        MeetingStatus status,
        Long recurrenceSeriesId,
        String room,
        Set<String> participantIds,
        List<MeetingEquipment> equipment,
        String recurrenceRule,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public MeetingResponse(Long id,String title,String description,OffsetDateTime startTime,OffsetDateTime endTime,
            String organizerId,MeetingStatus status,Long recurrenceSeriesId,OffsetDateTime createdAt,OffsetDateTime updatedAt){
        this(id,title,description,startTime,endTime,organizerId,status,recurrenceSeriesId,null,Set.of(),List.of(),null,createdAt,updatedAt);
    }

    public static MeetingResponse from(Meeting meeting) {
        return new MeetingResponse(
                meeting.getId(),
                meeting.getTitle(),
                meeting.getDescription(),
                meeting.getStartTime(),
                meeting.getEndTime(),
                meeting.getOrganizerId(),
                meeting.getStatus(),
                meeting.getRecurrenceSeriesId(),
                meeting.getRoom(),
                meeting.getParticipantIds(),
                meeting.getEquipment(),
                meeting.getRecurrenceRule(),
                meeting.getCreatedAt(),
                meeting.getUpdatedAt());
    }
}
