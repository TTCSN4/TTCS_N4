package com.example.demo.meeting;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MeetingParticipantRepository extends JpaRepository<MeetingParticipant, Long> {

    List<MeetingParticipant> findAllByMeetingIdOrderByIdAsc(Long meetingId);

    Optional<MeetingParticipant> findByIdAndMeetingId(Long id, Long meetingId);
}