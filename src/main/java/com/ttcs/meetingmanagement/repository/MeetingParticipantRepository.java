package com.ttcs.meetingmanagement.repository;

import com.ttcs.meetingmanagement.model.MeetingParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MeetingParticipantRepository extends JpaRepository<MeetingParticipant, String> {
    List<MeetingParticipant> findByMeetingId(String meetingId);
    List<MeetingParticipant> findByUserId(String userId);
}
