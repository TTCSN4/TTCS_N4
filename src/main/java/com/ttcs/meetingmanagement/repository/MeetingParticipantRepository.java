package com.ttcs.meetingmanagement.repository;

import com.ttcs.meetingmanagement.model.MeetingParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MeetingParticipantRepository extends JpaRepository<MeetingParticipant, String> {
    List<MeetingParticipant> findByMeetingId(String meetingId);
    void deleteByMeetingId(String meetingId);
}
