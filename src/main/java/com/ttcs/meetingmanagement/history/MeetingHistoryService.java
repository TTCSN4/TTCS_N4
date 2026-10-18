
package com.ttcs.meetingmanagement.history;

import com.ttcs.meetingmanagement.dto.MeetingHistoryResponse;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class MeetingHistoryService {

    private final NamedParameterJdbcTemplate jdbc;

    private static final ZoneId ZONE =
            ZoneId.of("Asia/Ho_Chi_Minh");

    public MeetingHistoryService(
            NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Lay danh sach cuoc hop da tham gia
    public List<MeetingHistoryResponse> getHistory(
            String userId) {

        validateId(userId);

        String sql = """
            SELECT
                m.meeting_id,
                m.title,
                m.description,
                m.room_id,
                m.organizer_id,
                m.start_time,
                m.end_time,
                m.status
            FROM meetings m
            WHERE m.end_time <= :now
              AND EXISTS (
                  SELECT 1
                  FROM check_in_log c
                  WHERE c.meeting_id = m.meeting_id
                    AND c.user_id = :userId
                    AND c.check_in_time IS NOT NULL
              )
            ORDER BY m.end_time DESC
            """;

        Map<String, Object> params = Map.of(
                "userId", userId.trim(),
                "now", LocalDateTime.now(ZONE)
        );

        return jdbc.query(sql, params, this::mapMeeting);
    }

    // Lay chi tiet mot cuoc hop da tham gia
    public MeetingHistoryResponse getHistoryDetail(
            String userId,
            String meetingId) {

        validateId(userId);
        validateId(meetingId);

        String sql = """
            SELECT
                m.meeting_id,
                m.title,
                m.description,
                m.room_id,
                m.organizer_id,
                m.start_time,
                m.end_time,
                m.status
            FROM meetings m
            WHERE m.meeting_id = :meetingId
              AND m.end_time <= :now
              AND EXISTS (
                  SELECT 1
                  FROM check_in_log c
                  WHERE c.meeting_id = m.meeting_id
                    AND c.user_id = :userId
                    AND c.check_in_time IS NOT NULL
              )
            """;

        Map<String, Object> params = Map.of(
                "userId", userId.trim(),
                "meetingId", meetingId.trim(),
                "now", LocalDateTime.now(ZONE)
        );

        List<MeetingHistoryResponse> results =
                jdbc.query(sql, params, this::mapMeeting);

        if (results.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Khong tim thay cuoc hop trong lich su");
        }

        return results.get(0);
    }

    private void validateId(String id) {
        if (id == null || id.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "ID khong hop le");
        }
    }

    private MeetingHistoryResponse mapMeeting(
            ResultSet rs,
            int rowNum) throws SQLException {

        return new MeetingHistoryResponse(
                rs.getString("meeting_id"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("room_id"),
                rs.getString("organizer_id"),
                rs.getObject(
                        "start_time", LocalDateTime.class),
                rs.getObject(
                        "end_time", LocalDateTime.class),
                rs.getString("status")
        );
    }
}
