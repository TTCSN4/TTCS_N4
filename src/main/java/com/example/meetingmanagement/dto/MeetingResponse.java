package com.example.meetingmanagement.dto;

import com.example.meetingmanagement.entity.Meeting;
import com.example.meetingmanagement.entity.MeetingStatus;
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
                meeting.getCreatedAt(),
                meeting.getUpdatedAt());
    }
}
