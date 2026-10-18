
package com.ttcs.meetingmanagement.service;

import com.ttcs.meetingmanagement.dto.CheckInRequest;
import com.ttcs.meetingmanagement.dto.CheckOutRequest;
import com.ttcs.meetingmanagement.dto.CheckInResponse;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class CheckInService {

    private static final ZoneId VN_ZONE =
            ZoneId.of("Asia/Ho_Chi_Minh");

    private final JdbcTemplate jdbc;
    private final QrCodeService qrCodeService;

    public CheckInService(
            JdbcTemplate jdbc,
            QrCodeService qrCodeService) {

        this.jdbc = jdbc;
        this.qrCodeService = qrCodeService;
    }

    // ====================================
    // 1. CHECK-IN
    // ====================================

    @Transactional
    public CheckInResponse checkIn(CheckInRequest request) {

        if (request == null
                || request.qrToken() == null
                || request.qrToken().isBlank()
                || request.userId() == null
                || request.userId().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "QR token and user ID are required"
            );
        }

        // Xac minh chu ky va thoi han QR
        String meetingId =
                qrCodeService.verifyToken(request.qrToken());

        String userId = request.userId().trim();

        LocalDateTime now = LocalDateTime.now(VN_ZONE);

        // Kiem tra nguoi dung co ton tai
        Integer userCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM `user` WHERE user_id = ?",
                Integer.class,
                userId
        );

        if (userCount == null || userCount == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "User not found"
            );
        }

        // Lay cuoc hop theo ERD
        List<MeetingInfo> meetings = jdbc.query(
                """
                SELECT
                    organizer_id,
                    room_id,
                    status,
                    start_time,
                    end_time
                FROM meeting
                WHERE meeting_id = ?
                """,
                (rs, rowNum) -> new MeetingInfo(
                        rs.getString("organizer_id"),
                        rs.getString("room_id"),
                        rs.getString("status"),
                        rs.getTimestamp("start_time")
                                .toLocalDateTime(),
                        rs.getTimestamp("end_time")
                                .toLocalDateTime()
                ),
                meetingId
        );

        if (meetings.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Meeting not found"
            );
        }

        MeetingInfo meeting = meetings.get(0);

        if (!"SCHEDULED".equalsIgnoreCase(meeting.status())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Meeting is not scheduled"
            );
        }

        if (meeting.roomId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Meeting has no booked room"
            );
        }

        // Cho check-in tu 30 phut truoc gio bat dau
        // den truoc khi cuoc hop ket thuc
        if (now.isBefore(meeting.startTime().minusMinutes(30))
                || !now.isBefore(meeting.endTime())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Outside check-in time window"
            );
        }

        // Chi organizer hoac nguoi tham du moi duoc check-in
        Integer participantCount = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM meeting_participant
                WHERE meeting_id = ?
                  AND user_id = ?
                """,
                Integer.class,
                meetingId,
                userId
        );

        boolean isOrganizer =
                userId.equals(meeting.organizerId());

        boolean isParticipant =
                participantCount != null && participantCount > 0;

        if (!isOrganizer && !isParticipant) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "User is not a participant"
            );
        }

        String logId = UUID.randomUUID().toString();

        // Chi luu hash QR, khong luu token goc
        String qrHash = hashToken(request.qrToken());

        try {
            jdbc.update(
                    """
                    INSERT INTO check_in_log (
                        log_id,
                        meeting_id,
                        user_id,
                        check_in_time,
                        check_out_time,
                        qr_code_data
                    )
                    VALUES (?, ?, ?, ?, NULL, ?)
                    """,
                    logId,
                    meetingId,
                    userId,
                    Timestamp.valueOf(now),
                    qrHash
            );

        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "User already checked in",
                    ex
            );
        }

        return new CheckInResponse(
                logId,
                meetingId,
                userId,
                now,
                null,
                "CHECKED_IN"
        );
    }

    // ====================================
    // 2. CHECK-OUT
    // ====================================

    @Transactional
    public CheckInResponse checkOut(CheckOutRequest request) {

        if (request == null
                || request.meetingId() == null
                || request.meetingId().isBlank()
                || request.userId() == null
                || request.userId().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Meeting ID and user ID are required"
            );
        }

        String meetingId = request.meetingId().trim();
        String userId = request.userId().trim();

        List<CheckInResponse> logs = jdbc.query(
                """
                SELECT
                    log_id,
                    meeting_id,
                    user_id,
                    check_in_time,
                    check_out_time
                FROM check_in_log
                WHERE meeting_id = ?
                  AND user_id = ?
                """,
                (rs, rowNum) -> {
                    Timestamp checkOut =
                            rs.getTimestamp("check_out_time");

                    return new CheckInResponse(
                            rs.getString("log_id"),
                            rs.getString("meeting_id"),
                            rs.getString("user_id"),
                            rs.getTimestamp("check_in_time")
                                    .toLocalDateTime(),
                            checkOut == null
                                    ? null
                                    : checkOut.toLocalDateTime(),
                            checkOut == null
                                    ? "CHECKED_IN"
                                    : "CHECKED_OUT"
                    );
                },
                meetingId,
                userId
        );

        if (logs.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "User has not checked in"
            );
        }

        CheckInResponse log = logs.get(0);

        if (log.checkOutTime() != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "User already checked out"
            );
        }

        LocalDateTime now = LocalDateTime.now(VN_ZONE);

        int updated = jdbc.update(
                """
                UPDATE check_in_log
                SET check_out_time = ?
                WHERE log_id = ?
                  AND check_out_time IS NULL
                """,
                Timestamp.valueOf(now),
                log.logId()
        );

        if (updated == 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Check-out already processed"
            );
        }

        return new CheckInResponse(
                log.logId(),
                meetingId,
                userId,
                log.checkInTime(),
                now,
                "CHECKED_OUT"
        );
    }

    // ====================================
    // 3. HASH QR TOKEN
    // ====================================

    private String hashToken(String token) {
        try {
            byte[] hash = MessageDigest
                    .getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable", ex
            );
        }
    }

    private record MeetingInfo(
            String organizerId,
            String roomId,
            String status,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {
    }
}
