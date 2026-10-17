
package com.ttcs.meetingmanagement.service;

import com.ttcs.meetingmanagement.dto.*;
import com.ttcs.meetingmanagement.model.MeetingParticipant;
import com.ttcs.meetingmanagement.repository.MeetingParticipantRepository;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class MeetingParticipantService {

    private final MeetingParticipantRepository repository;
    private final JdbcTemplate jdbcTemplate;

    public MeetingParticipantService(
            MeetingParticipantRepository repository,
            JdbcTemplate jdbcTemplate
    ) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
    }

    // KIEM TRA CUOC HOP TON TAI
    private boolean meetingExists(String meetingId) {

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM meeting WHERE meeting_id = ?",
                Integer.class,
                meetingId
        );

        return count != null && count > 0;
    }

    // KIEM TRA USER TON TAI
    private boolean userExists(String userId) {

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM `user` WHERE user_id = ?",
                Integer.class,
                userId
        );

        return count != null && count > 0;
    }

    private void validateMeeting(String meetingId) {

        if (meetingId == null || meetingId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Meeting ID khong hop le"
            );
        }

        if (!meetingExists(meetingId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Cuoc hop khong ton tai"
            );
        }
    }

    // 1. THEM NGUOI THAM DU - KHONG GUI EMAIL
    @Transactional
    public MeetingParticipantResponse addParticipant(
            String meetingId,
            AddMeetingParticipantRequest request
    ) {

        validateMeeting(meetingId);

        if (request == null
                || request.userId() == null
                || request.userId().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "User ID khong duoc de trong"
            );
        }

        String userId = request.userId().trim();

        if (!userExists(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Nguoi dung khong ton tai"
            );
        }

        if (repository.existsByMeetingIdAndUserId(
                meetingId, userId)) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Nguoi dung da tham gia cuoc hop"
            );
        }

        MeetingParticipant participant =
                new MeetingParticipant();

        participant.setParticipantId(
                UUID.randomUUID().toString()
        );
        participant.setMeetingId(meetingId);
        participant.setUserId(userId);
        participant.setStatus("PENDING");
        participant.setRespondedAt(null);

        MeetingParticipant saved =
                repository.save(participant);

        return MeetingParticipantResponse.from(saved);
    }

    // 2. LAY DANH SACH NGUOI THAM DU
    @Transactional(readOnly = true)
    public List<MeetingParticipantResponse> getParticipants(
            String meetingId
    ) {

        validateMeeting(meetingId);

        return repository.findByMeetingId(meetingId)
                .stream()
                .map(MeetingParticipantResponse::from)
                .toList();
    }

    // 3. XOA NGUOI THAM DU
    @Transactional
    public void removeParticipant(
            String meetingId,
            String userId
    ) {

        MeetingParticipant participant =
                findParticipant(meetingId, userId);

        repository.delete(participant);
    }

    // 4. CHAP NHAN HOAC TU CHOI TRUC TIEP
    @Transactional
    public MeetingParticipantResponse updateStatus(
            String meetingId,
            String userId,
            UpdateInvitationStatusRequest request
    ) {

        if (request == null
                || request.status() == null
                || request.status().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Trang thai khong duoc de trong"
            );
        }

        String newStatus = request.status()
                .trim()
                .toUpperCase();

        if (!Set.of("ACCEPTED", "DECLINED")
                .contains(newStatus)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Trang thai khong hop le"
            );
        }

        MeetingParticipant participant =
                findParticipant(meetingId, userId);

        if (!"PENDING".equals(participant.getStatus())) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Nguoi tham du da phan hoi"
            );
        }

        participant.setStatus(newStatus);
        participant.setRespondedAt(LocalDateTime.now());

        MeetingParticipant saved =
                repository.save(participant);

        return MeetingParticipantResponse.from(saved);
    }

    // TIM NGUOI THAM DU
    private MeetingParticipant findParticipant(
            String meetingId,
            String userId
    ) {

        validateMeeting(meetingId);

        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "User ID khong hop le"
            );
        }

        return repository
                .findByMeetingIdAndUserId(meetingId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Nguoi tham du khong ton tai"
                ));
    }
}
