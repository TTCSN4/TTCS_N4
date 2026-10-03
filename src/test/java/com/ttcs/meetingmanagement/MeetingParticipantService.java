package com.example.demo.meeting;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Sort;

import java.util.List;

@Service
public class MeetingParticipantService {

    private final MeetingRepository meetingRepository;
    private final MeetingParticipantRepository participantRepository;

    public MeetingParticipantService(
            MeetingRepository meetingRepository,
            MeetingParticipantRepository participantRepository
    ) {
        this.meetingRepository = meetingRepository;
        this.participantRepository = participantRepository;
    }

    public List<MeetingParticipant> getParticipants(Long meetingId) {
        ensureMeetingExists(meetingId);
        return participantRepository.findAllByMeetingIdOrderByIdAsc(meetingId);
    }

    public List<MeetingParticipant> getAllParticipants() {
        return participantRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
    }

    @Transactional
    public MeetingParticipant addParticipant(
            Long meetingId,
            String name,
            String email,
            String role,
            String status,
            String notes
    ) {
        ensureMeetingExists(meetingId);

        MeetingParticipant participant = new MeetingParticipant();
        participant.setMeetingId(meetingId);
        participant.setName(name.trim());
        participant.setEmail(clean(email));
        participant.setRole(clean(role));
        participant.setStatus(clean(status));
        participant.setNotes(clean(notes));
        return participantRepository.save(participant);
    }

    @Transactional
    public MeetingParticipant updateParticipant(
            Long meetingId,
            Long participantId,
            String name,
            String email,
            String role,
            String status,
            String notes
    ) {
        ensureMeetingExists(meetingId);
        MeetingParticipant participant = participantRepository
                .findByIdAndMeetingId(participantId, meetingId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy người tham dự trong cuộc họp này"
                ));

        participant.setName(name.trim());
        participant.setEmail(clean(email));
        participant.setRole(clean(role));
        participant.setStatus(clean(status));
        participant.setNotes(clean(notes));
        return participantRepository.save(participant);
    }

    @Transactional
    public void removeParticipant(Long meetingId, Long participantId) {
        MeetingParticipant participant = participantRepository
                .findByIdAndMeetingId(participantId, meetingId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy người tham dự trong cuộc họp này"
                ));
        participantRepository.delete(participant);
    }

    private void ensureMeetingExists(Long meetingId) {
        if (!meetingRepository.existsById(meetingId)) {
            throw new IllegalArgumentException("Không tìm thấy cuộc họp id = " + meetingId);
        }
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}