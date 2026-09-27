package com.example.meetingmanagement.service;

import com.example.meetingmanagement.dto.MeetingResponse;
import com.example.meetingmanagement.dto.UpdateMeetingRequest;
import com.example.meetingmanagement.entity.Meeting;
import com.example.meetingmanagement.entity.MeetingStatus;
import com.example.meetingmanagement.exception.MeetingConflictException;
import com.example.meetingmanagement.exception.MeetingForbiddenException;
import com.example.meetingmanagement.exception.MeetingNotFoundException;
import com.example.meetingmanagement.repository.MeetingRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MeetingService {

    private final MeetingRepository meetingRepository;
    private final Clock clock;

    public MeetingService(MeetingRepository meetingRepository, Clock clock) {
        this.meetingRepository = meetingRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public MeetingResponse getMeeting(Long meetingId) {
        return meetingRepository.findById(meetingId)
                .map(MeetingResponse::from)
                .orElseThrow(() -> new MeetingNotFoundException(meetingId));
    }

    @Transactional(readOnly = true)
    public List<MeetingResponse> getMeetings() {
        return meetingRepository.findAllByOrderByIdAsc().stream()
                .map(MeetingResponse::from)
                .toList();
    }

    @Transactional
    public MeetingResponse updateMeeting(Long meetingId, String actorId, UpdateMeetingRequest request) {
        Meeting meeting = getMeetingForUpdate(meetingId);
        verifyOrganizer(meeting, actorId);
        verifyScheduled(meeting);

        meeting.setTitle(request.title().trim());
        meeting.setDescription(request.description());
        meeting.setStartTime(request.startTime());
        meeting.setEndTime(request.endTime());
        meetingRepository.saveAndFlush(meeting);
        return MeetingResponse.from(meeting);
    }

    @Transactional
    public void cancelMeeting(Long meetingId, String actorId) {
        Meeting meeting = getMeetingForUpdate(meetingId);
        verifyOrganizer(meeting, actorId);
        verifyScheduled(meeting);
        meeting.setStatus(MeetingStatus.CANCELLED);
        meetingRepository.saveAndFlush(meeting);
    }

    private Meeting getMeetingForUpdate(Long meetingId) {
        return meetingRepository.findByIdForUpdate(meetingId)
                .orElseThrow(() -> new MeetingNotFoundException(meetingId));
    }

    private void verifyOrganizer(Meeting meeting, String actorId) {
        if (!meeting.getOrganizerId().equalsIgnoreCase(actorId)) {
            throw new MeetingForbiddenException();
        }
    }

    private void verifyScheduled(Meeting meeting) {
        if (meeting.getStatus() != MeetingStatus.SCHEDULED) {
            throw new MeetingConflictException("Only scheduled meetings can be modified or cancelled");
        }
        if (!meeting.getStartTime().isAfter(OffsetDateTime.now(clock))) {
            throw new MeetingConflictException("A meeting that has already started cannot be modified or cancelled");
        }
    }
}
