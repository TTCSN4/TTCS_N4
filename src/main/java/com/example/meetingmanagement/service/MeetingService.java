package com.example.meetingmanagement.service;

import com.example.meetingmanagement.dto.MeetingResponse;
import com.example.meetingmanagement.dto.UpdateMeetingRequest;
import com.example.meetingmanagement.entity.Meeting;
import com.example.meetingmanagement.entity.MeetingStatus;
import com.example.meetingmanagement.entity.MeetingEquipment;
import com.example.meetingmanagement.exception.MeetingConflictException;
import com.example.meetingmanagement.exception.MeetingForbiddenException;
import com.example.meetingmanagement.exception.MeetingNotFoundException;
import com.example.meetingmanagement.repository.MeetingRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.time.Duration;
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
    public MeetingResponse getMeeting(String meetingId) {
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
    public MeetingResponse updateMeeting(String meetingId, String actorId, UpdateMeetingRequest request) {
        Meeting meeting = getMeetingForUpdate(meetingId);
        verifyOrganizer(meeting, actorId);
        verifyScheduled(meeting);

        List<Meeting> targets=request.scope()==UpdateMeetingRequest.UpdateScope.SERIES?series(meeting):List.of(meeting);
        Duration shift=Duration.between(meeting.getStartTime(),request.startTime());
        Duration duration=Duration.between(request.startTime(),request.endTime());
        for(Meeting target:targets){OffsetDateTime start=target==meeting?request.startTime():target.getStartTime().plus(shift);OffsetDateTime end=start.plus(duration);
            String room=request.room()==null?target.getRoom():request.room().trim();
            if(room!=null&&!room.isBlank()&&meetingRepository.countRoomConflicts(room,target.getId(),start,end)>0)throw new MeetingConflictException("Room is already booked");
            target.setTitle(request.title().trim());target.setDescription(request.description());target.setStartTime(start);target.setEndTime(end);target.setRoom(room==null||room.isBlank()?null:room);
            if(request.participantIds()!=null)target.setParticipantIds(request.participantIds());
            if(request.equipment()!=null)target.setEquipment(request.equipment().stream().map(e->new MeetingEquipment(e.equipmentId().trim(),e.quantity())).toList());
            if(request.recurrenceRule()!=null)target.setRecurrenceRule(request.recurrenceRule().isBlank()?null:request.recurrenceRule().trim());meetingRepository.save(target);}
        meetingRepository.saveAndFlush(meeting);
        return MeetingResponse.from(meeting);
    }

    private List<Meeting> series(Meeting meeting){
        if(meeting.getRecurrenceSeriesId()==null)throw new MeetingConflictException("Meeting is not part of a recurring series");
        return meetingRepository.findByRecurrenceSeriesIdAndStartTimeGreaterThanEqualOrderByStartTimeAsc(meeting.getRecurrenceSeriesId(),meeting.getStartTime());
    }

    @Transactional
    public void cancelMeeting(String meetingId, String actorId) {
        Meeting meeting = getMeetingForUpdate(meetingId);
        verifyOrganizer(meeting, actorId);
        verifyScheduled(meeting);
        meeting.setStatus(MeetingStatus.CANCELLED);
        meeting.setCancelledAt(OffsetDateTime.now(clock));
        meetingRepository.saveAndFlush(meeting);
    }

    private Meeting getMeetingForUpdate(String meetingId) {
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
