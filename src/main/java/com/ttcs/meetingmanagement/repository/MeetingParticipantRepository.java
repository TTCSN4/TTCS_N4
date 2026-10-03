package com.ttcs.meetingmanagement.repository;

import com.ttcs.meetingmanagement.model.MeetingParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MeetingParticipantRepository extends JpaRepository<MeetingParticipant, Long> {

    List<MeetingParticipant> findAllByMeetingIdOrderByIdAsc(Long meetingId);

    Optional<MeetingParticipant> findByIdAndMeetingId(Long id, Long meetingId);
}