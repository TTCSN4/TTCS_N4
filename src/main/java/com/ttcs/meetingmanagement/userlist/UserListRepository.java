
package com.ttcs.meetingmanagement.userlist;

import com.ttcs.meetingmanagement.userlist.UserListResponse.UserSummary;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Repository
public class UserListRepository {

    private final JdbcTemplate jdbcTemplate;

    public UserListRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // Su dung dung bang USER va ROLE theo ERD.
    private static final String SELECT_COLUMNS = """
            SELECT
                u.user_id,
                u.full_name,
                u.email,
                u.phone,
                u.status,
                u.role_id,
                r.role_name,
                u.department_id,
                u.created_at
            """;

    private static final String FROM_CLAUSE = """
            FROM `USER` u
            LEFT JOIN `ROLE` r
                ON r.role_id = u.role_id
            """;

    // Chua dieu kien SQL va danh sach tham so.
    private record Filter(
            String sql,
            List<Object> params
    ) {
    }

    // Xay dung dieu kien tim kiem va loc.
    // Tat ca gia tri nguoi dung duoc bind bang ?.
    private Filter buildFilter(
            String search,
            String status,
            String roleId
    ) {

        StringBuilder where =
                new StringBuilder(" WHERE 1 = 1 ");

        List<Object> params = new ArrayList<>();

        // Tim theo ho ten hoac email.
        if (search != null && !search.isBlank()) {

            String keyword =
                    search.trim().toLowerCase(Locale.ROOT);

            // Cac ky tu % va _ duoc xem la
            // van ban tim kiem, khong phai wildcard.
            String escaped = keyword
                    .replace("!", "!!")
                    .replace("%", "!%")
                    .replace("_", "!_");

            String pattern = "%" + escaped + "%";

            where.append("""
                    AND (
                        LOWER(COALESCE(u.full_name, ''))
                            LIKE ? ESCAPE '!'
                        OR
                        LOWER(COALESCE(u.email, ''))
                            LIKE ? ESCAPE '!'
                    )
                    """);

            params.add(pattern);
            params.add(pattern);
        }

        // Loc theo trang thai.
        if (status != null && !status.isBlank()) {

            where.append("""
                    AND UPPER(TRIM(COALESCE(u.status, '')))
                        = ?
                    """);

            params.add(
                    status.trim().toUpperCase(Locale.ROOT)
            );
        }

        // Loc theo ma vai tro trong ERD.
        if (roleId != null && !roleId.isBlank()) {

            where.append(" AND u.role_id = ? ");

            params.add(roleId.trim());
        }

        return new Filter(
                where.toString(),
                params
        );
    }

    // 1. Lay danh sach nguoi dung co phan trang.
    public List<UserSummary> findUsers(
            String search,
            String status,
            String roleId,
            int size,
            long offset
    ) {

        Filter filter =
                buildFilter(search, status, roleId);

        String sql =
                SELECT_COLUMNS
                + FROM_CLAUSE
                + filter.sql()
                + """
                  ORDER BY
                      u.created_at DESC,
                      u.user_id ASC
                  LIMIT ? OFFSET ?
                  """;

        List<Object> params =
                new ArrayList<>(filter.params());

        params.add(size);
        params.add(offset);

        return jdbcTemplate.query(
                sql,
                UserListRepository::mapUser,
                params.toArray()
        );
    }

    // 2. Dem tong so nguoi dung phu hop.
    // Khong ap dung LIMIT/OFFSET tai day.
    public long countUsers(
            String search,
            String status,
            String roleId
    ) {

        Filter filter =
                buildFilter(search, status, roleId);

        String sql =
                "SELECT COUNT(*) "
                + FROM_CLAUSE
                + filter.sql();

        Long total = jdbcTemplate.queryForObject(
                sql,
                Long.class,
                filter.params().toArray()
        );

        return total == null ? 0L : total;
    }

    // 3. Xem thong tin co ban theo user_id.
    public Optional<UserSummary> findById(
            String userId
    ) {

        String sql =
                SELECT_COLUMNS
                + FROM_CLAUSE
                + " WHERE u.user_id = ? ";

        List<UserSummary> users = jdbcTemplate.query(
                sql,
                UserListRepository::mapUser,
                userId
        );

        return users.stream().findFirst();
    }

    // 4. Chuyen du lieu SQL sang UserSummary DTO.
    private static UserSummary mapUser(
            ResultSet rs,
            int rowNum
    ) throws SQLException {

        Timestamp createdAt =
                rs.getTimestamp("created_at");

        return new UserSummary(
                rs.getString("user_id"),
                rs.getString("full_name"),
                rs.getString("email"),
                rs.getString("phone"),
                rs.getString("status"),
                rs.getString("role_id"),
                rs.getString("role_name"),
                rs.getString("department_id"),
                createdAt == null
                        ? null
                        : createdAt.toLocalDateTime()
        );
    }
}
