package com.ttcs.meetingmanagement.dto;

import com.ttcs.meetingmanagement.model.Meeting;
import com.ttcs.meetingmanagement.model.MeetingStatus;
import java.time.OffsetDateTime;

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
        Integer participantCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

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
                meeting.getParticipantCount(),
                meeting.getCreatedAt(),
                meeting.getUpdatedAt());
    }
}
