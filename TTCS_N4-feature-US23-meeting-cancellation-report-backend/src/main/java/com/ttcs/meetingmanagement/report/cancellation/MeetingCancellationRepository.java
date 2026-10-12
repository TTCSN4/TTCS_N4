
package com.ttcs.meetingmanagement.report.cancellation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
public class MeetingCancellationRepository {

    private final JdbcTemplate jdbcTemplate;

    public MeetingCancellationRepository(
            JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // Du lieu cuoc hop phuc vu thong ke
    public record MeetingRow(
            String meetingId,
            String roomId,
            String roomName,
            String organizerId,
            String organizerName,
            LocalDateTime startTime,
            String status
    ) {
    }

    // Kiem tra phong ton tai trong bang ROOM
    public boolean roomExists(String roomId) {

        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM `ROOM` WHERE room_id = ?",
                Long.class,
                roomId
        );

        return count != null && count > 0;
    }

    // Kiem tra nguoi to chuc trong bang USER
    public boolean organizerExists(String organizerId) {

        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM `USER` WHERE user_id = ?",
                Long.class,
                organizerId
        );

        return count != null && count > 0;
    }

    // Truy van cuoc hop theo khoang thoi gian,
    // phong hop va nguoi to chuc.
    public List<MeetingRow> findMeetings(
            LocalDateTime fromInclusive,
            LocalDateTime toExclusive,
            String roomId,
            String organizerId
    ) {

        StringBuilder sql = new StringBuilder("""
            SELECT
                m.meeting_id,
                m.room_id,
                r.room_name,
                m.organizer_id,
                u.full_name AS organizer_name,
                m.start_time,
                m.status
            FROM `MEETING` m
            LEFT JOIN `ROOM` r
                ON r.room_id = m.room_id
            LEFT JOIN `USER` u
                ON u.user_id = m.organizer_id
            WHERE m.start_time >= ?
              AND m.start_time < ?
            """);

        List<Object> params = new ArrayList<>();

        params.add(Timestamp.valueOf(fromInclusive));
        params.add(Timestamp.valueOf(toExclusive));

        // Loc theo phong neu co roomId
        if (roomId != null) {
            sql.append(" AND m.room_id = ?");
            params.add(roomId);
        }

        // Loc theo nguoi to chuc neu co organizerId
        if (organizerId != null) {
            sql.append(" AND m.organizer_id = ?");
            params.add(organizerId);
        }

        sql.append(
                " ORDER BY m.start_time, m.meeting_id"
        );

        return jdbcTemplate.query(
                sql.toString(),
                (rs, rowNum) -> new MeetingRow(
                        rs.getString("meeting_id"),
                        rs.getString("room_id"),
                        rs.getString("room_name"),
                        rs.getString("organizer_id"),
                        rs.getString("organizer_name"),
                        rs.getTimestamp("start_time")
                                .toLocalDateTime(),
                        rs.getString("status")
                ),
                params.toArray()
        );
    }
}
