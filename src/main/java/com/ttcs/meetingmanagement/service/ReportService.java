
package com.ttcs.meetingmanagement.service;

import com.ttcs.meetingmanagement.dto.MeetingReportResponse;
import com.ttcs.meetingmanagement.dto.ReportFilterRequest;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReportService {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ReportService(
            NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<MeetingReportResponse> getMeetingReport(
            ReportFilterRequest filter) {

        // Kiem tra khoang ngay
        if (filter.fromDate() != null
                && filter.toDate() != null
                && filter.fromDate().isAfter(filter.toDate())) {

            throw new IllegalArgumentException(
                    "fromDate khong duoc lon hon toDate");
        }

        // Truy van dung cac bang theo ERD
        StringBuilder sql = new StringBuilder("""
            SELECT
                m.meeting_id,
                m.title,
                m.organizer_id,
                u.full_name AS organizer_name,
                m.room_id,
                r.room_name,
                m.start_time,
                m.end_time,
                m.status,
                m.cancel_reason,
                m.cancelled_at
            FROM meeting m
            LEFT JOIN `user` u
                ON m.organizer_id = u.user_id
            LEFT JOIN room r
                ON m.room_id = r.room_id
            WHERE 1 = 1
            """);

        MapSqlParameterSource params =
                new MapSqlParameterSource();

        // Loc tu ngay (theo start_time)
        if (filter.fromDate() != null) {
            sql.append(
                    " AND m.start_time >= :fromDate ");

            params.addValue(
                    "fromDate",
                    filter.fromDate().atStartOfDay()
            );
        }

        // Loc den het ngay (theo start_time)
        if (filter.toDate() != null) {
            sql.append(
                    " AND m.start_time < :toDate ");

            params.addValue(
                    "toDate",
                    filter.toDate().plusDays(1).atStartOfDay()
            );
        }

        // Loc trang thai cuoc hop
        if (hasText(filter.status())) {
            sql.append(
                    " AND m.status = :status ");

            params.addValue(
                    "status",
                    filter.status().trim()
            );
        }

        // Loc theo phong
        if (hasText(filter.roomId())) {
            sql.append(
                    " AND m.room_id = :roomId ");

            params.addValue(
                    "roomId",
                    filter.roomId().trim()
            );
        }

        // Loc theo nguoi to chuc
        if (hasText(filter.organizerId())) {
            sql.append(
                    " AND m.organizer_id = :organizerId ");

            params.addValue(
                    "organizerId",
                    filter.organizerId().trim()
            );
        }

        // Tim kiem ten cuoc hop
        if (hasText(filter.keyword())) {
            sql.append(
                    " AND LOWER(m.title) LIKE LOWER(:keyword) ");

            params.addValue(
                    "keyword",
                    "%" + filter.keyword().trim() + "%"
            );
        }

        sql.append(
                " ORDER BY m.start_time DESC, m.meeting_id ASC ");

        return jdbcTemplate.query(
                sql.toString(),
                params,
                (rs, rowNum) -> new MeetingReportResponse(
                        rs.getString("meeting_id"),
                        rs.getString("title"),
                        rs.getString("organizer_id"),
                        rs.getString("organizer_name"),
                        rs.getString("room_id"),
                        rs.getString("room_name"),
                        getDateTime(rs, "start_time"),
                        getDateTime(rs, "end_time"),
                        rs.getString("status"),
                        rs.getString("cancel_reason"),
                        getDateTime(rs, "cancelled_at")
                )
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private LocalDateTime getDateTime(
            ResultSet rs,
            String column) throws SQLException {

        Timestamp timestamp = rs.getTimestamp(column);

        return timestamp == null
                ? null
                : timestamp.toLocalDateTime();
    }
}
