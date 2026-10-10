
package com.ttcs.meetingmanagement.service;

import com.ttcs.meetingmanagement.dto.RoomSuggestionRequest;
import com.ttcs.meetingmanagement.dto.RoomSuggestionResponse;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.util.List;

@Service
public class RoomSuggestionService {

    private final JdbcTemplate jdbcTemplate;

    public RoomSuggestionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // US26: Goi y phong theo so nguoi, thoi gian,
    // suc chua va trang thai phong
    public List<RoomSuggestionResponse> suggestRooms(
            RoomSuggestionRequest request) {

        // 1. Kiem tra du lieu dau vao
        if (request == null
                || request.participantCount() == null
                || request.participantCount() < 1
                || request.startTime() == null
                || request.endTime() == null
                || !request.endTime().isAfter(request.startTime())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid participant count or meeting time"
            );
        }

        // 2. Loc phong AVAILABLE, du suc chua,
        // khong trung lich va uu tien phong vua du
        String sql = """
                SELECT
                    r.room_id,
                    r.room_name,
                    r.capacity,
                    r.status,
                    (r.capacity - ?) AS spare_capacity
                FROM room r
                WHERE r.capacity >= ?

                  AND UPPER(TRIM(r.status)) = 'AVAILABLE'

                  AND NOT EXISTS (
                      SELECT 1
                      FROM meeting m
                      WHERE m.room_id = r.room_id

                        AND m.start_time < ?
                        AND m.end_time > ?

                        AND (
                            m.status IS NULL
                            OR UPPER(TRIM(m.status))
                                NOT IN ('CANCELLED', 'CANCELED')
                        )
                  )

                ORDER BY
                    spare_capacity ASC,
                    r.capacity ASC,
                    r.room_id ASC
                """;

        // 3. Thuc hien truy van va tra ve danh sach phong
        return jdbcTemplate.query(
                sql,

                (rs, rowNum) -> new RoomSuggestionResponse(
                        rs.getString("room_id"),
                        rs.getString("room_name"),
                        rs.getInt("capacity"),
                        rs.getString("status"),
                        rs.getInt("spare_capacity")
                ),

                request.participantCount(),
                request.participantCount(),
                Timestamp.valueOf(request.endTime()),
                Timestamp.valueOf(request.startTime())
        );
    }
}
