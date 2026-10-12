
package com.ttcs.meetingmanagement.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RoomRestrictionRepository {

    private final JdbcTemplate jdbcTemplate;

    public RoomRestrictionRepository(
            JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // Kiem tra user ton tai
    public boolean userExists(String userId) {

        String sql = """
            SELECT COUNT(*)
            FROM `user`
            WHERE user_id = ?
            """;

        Integer count = jdbcTemplate.queryForObject(
                sql, Integer.class, userId
        );

        return count != null && count > 0;
    }

    // Kiem tra phong ton tai
    public boolean roomExists(String roomId) {

        String sql = """
            SELECT COUNT(*)
            FROM room
            WHERE room_id = ?
            """;

        Integer count = jdbcTemplate.queryForObject(
                sql, Integer.class, roomId
        );

        return count != null && count > 0;
    }

    // Kiem tra phong co chinh sach gioi han khong
    public boolean hasRestrictions(String roomId) {

        String sql = """
            SELECT COUNT(*)
            FROM room_restriction
            WHERE room_id = ?
            """;

        Integer count = jdbcTemplate.queryForObject(
                sql, Integer.class, roomId
        );

        return count != null && count > 0;
    }

    // Kiem tra quyen theo USER, ROLE, DEPARTMENT
    public boolean isAllowed(
            String roomId,
            String userId) {

        String sql = """
            SELECT COUNT(*)
            FROM room_restriction rr
            JOIN `user` u
                ON u.user_id = ?
            WHERE rr.room_id = ?
              AND (
                  rr.user_id = u.user_id
                  OR rr.role_id = u.role_id
                  OR rr.department_id = u.department_id
              )
            """;

        Integer count = jdbcTemplate.queryForObject(
                sql,
                Integer.class,
                userId,
                roomId
        );

        return count != null && count > 0;
    }
}
