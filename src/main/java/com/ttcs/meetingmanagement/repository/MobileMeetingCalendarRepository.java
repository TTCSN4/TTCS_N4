
package com.ttcs.meetingmanagement.repository;

import com.ttcs.meetingmanagement.dto.MobileMeetingDetailResponse;
import com.ttcs.meetingmanagement.dto.MobileMeetingSummaryResponse;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class MobileMeetingCalendarRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public MobileMeetingCalendarRepository(
            NamedParameterJdbcTemplate jdbc
    ) {
        this.jdbc = jdbc;
    }

    // CHI LAY CUOC HOP USER TO CHUC HOAC DUOC MOI
    private static final String VISIBLE = """
        (
            m.organizer_id = :userId
            OR EXISTS (
                SELECT 1
                FROM meeting_participant mp
                WHERE mp.meeting_id = m.meeting_id
                  AND mp.user_id = :userId
            )
        )
        """;

    // DEM SO CUOC HOP DUOC PHEP XEM
    public long countVisibleMeetings(
            String userId,
            LocalDateTime fromInclusive,
            LocalDateTime toExclusive
    ) {

        String sql = """
            SELECT COUNT(*)
            FROM meeting m
            WHERE m.start_time < :toExclusive
              AND m.end_time > :fromInclusive
              AND
            """ + VISIBLE;

        Long count = jdbc.queryForObject(
                sql,
                commonParams(
                        userId,
                        fromInclusive,
                        toExclusive
                ),
                Long.class
        );

        return count == null ? 0L : count;
    }

    // LAY DANH SACH LICH HOP PHAN TRANG
    public List<MobileMeetingSummaryResponse> findVisibleMeetings(
            String userId,
            LocalDateTime fromInclusive,
            LocalDateTime toExclusive,
            int limit,
            long offset
    ) {

        String sql = """
            SELECT
                m.meeting_id,
                m.title,
                m.start_time,
                m.end_time,
                m.status,
                r.room_name,
                CASE
                    WHEN m.organizer_id = :userId
                    THEN 1
                    ELSE 0
                END AS is_organizer
            FROM meeting m
            LEFT JOIN room r
                ON m.room_id = r.room_id
            WHERE m.start_time < :toExclusive
              AND m.end_time > :fromInclusive
              AND
            """ + VISIBLE + """
            ORDER BY m.start_time ASC, m.meeting_id ASC
            LIMIT :limit OFFSET :offset
            """;

        MapSqlParameterSource params =
                commonParams(
                        userId,
                        fromInclusive,
                        toExclusive
                )
                .addValue("limit", limit)
                .addValue("offset", offset);

        return jdbc.query(
                sql,
                params,
                (rs, rowNum) -> new MobileMeetingSummaryResponse(
                        rs.getString("meeting_id"),
                        rs.getString("title"),
                        rs.getTimestamp("start_time")
                                .toLocalDateTime(),
                        rs.getTimestamp("end_time")
                                .toLocalDateTime(),
                        rs.getString("status"),
                        rs.getString("room_name"),
                        rs.getBoolean("is_organizer")
                )
        );
    }

    // LAY CHI TIET CUOC HOP
    public Optional<MobileMeetingDetailResponse> findVisibleById(
            String userId,
            String meetingId
    ) {

        String sql = """
            SELECT
                m.meeting_id,
                m.title,
                m.description,
                m.start_time,
                m.end_time,
                m.status,
                m.organizer_id,
                m.room_id,
                r.room_name,
                r.location,
                CASE
                    WHEN m.organizer_id = :userId
                    THEN 1
                    ELSE 0
                END AS is_organizer
            FROM meeting m
            LEFT JOIN room r
                ON m.room_id = r.room_id
            WHERE m.meeting_id = :meetingId
              AND
            """ + VISIBLE;

        MapSqlParameterSource params =
                new MapSqlParameterSource()
                        .addValue("userId", userId)
                        .addValue("meetingId", meetingId);

        List<MobileMeetingDetailResponse> results =
                jdbc.query(
                        sql,
                        params,
                        (rs, rowNum) ->
                                new MobileMeetingDetailResponse(
                                        rs.getString("meeting_id"),
                                        rs.getString("title"),
                                        rs.getString("description"),
                                        rs.getTimestamp("start_time")
                                                .toLocalDateTime(),
                                        rs.getTimestamp("end_time")
                                                .toLocalDateTime(),
                                        rs.getString("status"),
                                        rs.getString("organizer_id"),
                                        rs.getString("room_id"),
                                        rs.getString("room_name"),
                                        rs.getString("location"),
                                        rs.getBoolean("is_organizer")
                                )
                );

        return results.stream().findFirst();
    }

    private MapSqlParameterSource commonParams(
            String userId,
            LocalDateTime fromInclusive,
            LocalDateTime toExclusive
    ) {

        return new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("fromInclusive", fromInclusive)
                .addValue("toExclusive", toExclusive);
    }
}
