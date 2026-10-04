package com.ttcs.meetingmanagement.service;

import com.ttcs.meetingmanagement.dto.MeetingResponse;
import com.ttcs.meetingmanagement.dto.CreateMeetingRequest;
import com.ttcs.meetingmanagement.dto.UpdateMeetingRequest;
import com.ttcs.meetingmanagement.exception.MeetingConflictException;
import com.ttcs.meetingmanagement.exception.MeetingForbiddenException;
import com.ttcs.meetingmanagement.exception.MeetingNotFoundException;
import com.ttcs.meetingmanagement.model.Meeting;
import com.ttcs.meetingmanagement.model.MeetingStatus;
import com.ttcs.meetingmanagement.repository.MeetingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

@Service
public class MeetingService {

    private final MeetingRepository meetingRepository;
    private final Clock clock;

    public MeetingService(MeetingRepository meetingRepository, Clock clock) {
        this.meetingRepository = meetingRepository;
        this.clock = clock;
    }

    @Transactional
    public Meeting createMeeting(CreateMeetingRequest request) {
        if (!request.getStartTime().isBefore(request.getEndTime())) {
            throw new IllegalArgumentException(
                    "Thá»i gian báº¯t Ä‘áº§u pháº£i trÆ°á»›c thá»i gian káº¿t thÃºc"
            );
        }

        Meeting meeting = new Meeting();
        meeting.setTitle(request.getTitle().trim());
        meeting.setDescription(request.getDescription());
        meeting.setStartTime(request.getStartTime());
        meeting.setEndTime(request.getEndTime());

        String organizerId = request.getOrganizerId();
        if (organizerId == null || organizerId.isBlank()) {
            organizerId = "user01";
        }
        meeting.setOrganizerId(organizerId);
        meeting.setRecurrenceSeriesId(request.getRecurrenceSeriesId());
        meeting.setRoom(request.getRoom());
        meeting.setParticipantCount(request.getParticipantCount());

        if (request.getStatus() == null) {
            meeting.setStatus(MeetingStatus.SCHEDULED);
        } else {
            meeting.setStatus(request.getStatus());
        }

        if (meeting.getRoom() != null && !meeting.getRoom().isBlank()
                && meetingRepository.existsByRoomIgnoreCaseAndStatusNotAndStartTimeLessThanAndEndTimeGreaterThan(
                        meeting.getRoom(), MeetingStatus.CANCELLED, meeting.getEndTime(), meeting.getStartTime())) {
            throw new MeetingConflictException("Phòng " + meeting.getRoom() + " đã được đặt trong khung giờ này.");
        }

        return meetingRepository.save(meeting);
    }

    @Transactional(readOnly = true)
    public MeetingResponse getMeeting(Long meetingId) {
        return meetingRepository.findById(meetingId)
                .map(MeetingResponse::from)
                .orElseThrow(() -> new MeetingNotFoundException(meetingId));
    }

    @Transactional(readOnly = true)
    public Meeting getMeetingById(Long id) {
        return meetingRepository.findById(id)
                .orElseThrow(() -> new MeetingNotFoundException(id));
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
        if (actorId == null || meeting.getOrganizerId() == null || !meeting.getOrganizerId().equalsIgnoreCase(actorId.trim())) {
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