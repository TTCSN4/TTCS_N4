package com.ttcs.meetingmanagement.dto;
import com.ttcs.meetingmanagement.model.Meeting;
import java.time.OffsetDateTime;
public record MeetingResponse(Long id,String title,String description,String room,OffsetDateTime startTime,OffsetDateTime endTime){public static MeetingResponse from(Meeting m){return new MeetingResponse(m.getId(),m.getTitle(),m.getDescription(),m.getRoom(),m.getStartTime(),m.getEndTime());}}
