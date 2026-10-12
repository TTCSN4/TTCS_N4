
package com.ttcs.meetingmanagement.report;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class RoomUsageRepository {

    private final JdbcTemplate jdbcTemplate;

    public RoomUsageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record RoomRow(
            String roomId,
            String roomName
    ) {
    }

    public record MeetingRow(
            String meetingId,
            String roomId,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {
    }

    // Lay cac phong tu bang ROOM theo ERD
    public List<RoomRow> findRooms(String roomId) {

        String sql = """
            SELECT room_id, room_name
            FROM `room`
            """;

        if (roomId != null) {
            sql += " WHERE room_id = ?";
        }

        sql += " ORDER BY room_id";

        if (roomId == null) {
            return jdbcTemplate.query(
                    sql,
                    (rs, rowNum) -> new RoomRow(
                            rs.getString("room_id"),
                            rs.getString("room_name")
                    )
            );
        }

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new RoomRow(
                        rs.getString("room_id"),
                        rs.getString("room_name")
                ),
                roomId
        );
    }

    // Lay cac cuoc hop co thoi gian giao voi ky bao cao
    public List<MeetingRow> findMeetings(
            LocalDateTime from,
            LocalDateTime toExclusive,
            String roomId
    ) {

        String sql = """
            SELECT
                m.meeting_id,
                m.room_id,
                m.start_time,
                m.end_time
            FROM `meeting` m
            INNER JOIN `room` r
                ON r.room_id = m.room_id
            WHERE m.start_time < ?
              AND m.end_time > ?
              AND m.end_time > m.start_time
              AND UPPER(TRIM(COALESCE(m.status, '')))
                  NOT IN ('CANCELLED', 'CANCELED')
            """;

        if (roomId != null) {
            sql += " AND m.room_id = ?";
        }

        sql += """
            
            ORDER BY m.room_id, m.start_time, m.meeting_id
            """;

        Object[] params;

        if (roomId == null) {
            params = new Object[] {
                    Timestamp.valueOf(toExclusive),
                    Timestamp.valueOf(from)
            };
        } else {
            params = new Object[] {
                    Timestamp.valueOf(toExclusive),
                    Timestamp.valueOf(from),
                    roomId
            };
        }

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new MeetingRow(
                        rs.getString("meeting_id"),
                        rs.getString("room_id"),
                        rs.getTimestamp("start_time")
                                .toLocalDateTime(),
                        rs.getTimestamp("end_time")
                                .toLocalDateTime()
                ),
                params
        );
    }
}
