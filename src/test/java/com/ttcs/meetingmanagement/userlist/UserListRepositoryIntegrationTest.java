package com.ttcs.meetingmanagement.userlist;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@JdbcTest(properties = "spring.datasource.url=jdbc:h2:mem:us19_repository;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@ContextConfiguration(classes = UserListRepositoryIntegrationTest.Config.class)
class UserListRepositoryIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserListRepository repository;

    @BeforeEach
    void setUp() {
        jdbc.execute("DROP TABLE IF EXISTS `USER`");
        jdbc.execute("DROP TABLE IF EXISTS `ROLE`");
        jdbc.execute("CREATE TABLE `ROLE` (role_id VARCHAR(50) PRIMARY KEY, role_name VARCHAR(100))");
        jdbc.execute("CREATE TABLE `USER` (user_id VARCHAR(50) PRIMARY KEY, full_name VARCHAR(255), email VARCHAR(255), phone VARCHAR(50), status VARCHAR(30), role_id VARCHAR(50), department_id VARCHAR(50), created_at TIMESTAMP, password_hash VARCHAR(255))");
        jdbc.update("INSERT INTO `ROLE` VALUES (?, ?)", "R_ADMIN", "Admin");
        jdbc.update("INSERT INTO `ROLE` VALUES (?, ?)", "R_MEMBER", "Member");
        insert("U1", "Nguyễn Văn A", "a_percent%_!@example.com", "ACTIVE", "R_ADMIN", 1);
        insert("U2", "nguyễn văn B", "b@example.com", "INACTIVE", "R_MEMBER", 2);
        insert("U3", "Alice", "alice@example.com", "ACTIVE", "R_MEMBER", 3);
        insert("U4", "ALICE Two", "two@example.com", "ACTIVE", "R_MEMBER", 4);
    }

    @Test
    void joinsMapsCountsAndPaginatesWithStableOrdering() {
        assertEquals(4, repository.countUsers(null, null, null));
        List<UserListResponse.UserSummary> first = repository.findUsers(null, null, null, 2, 0);
        List<UserListResponse.UserSummary> second = repository.findUsers(null, null, null, 2, 2);

        assertEquals(List.of("U4", "U3"), first.stream().map(UserListResponse.UserSummary::userId).toList());
        assertEquals(List.of("U2", "U1"), second.stream().map(UserListResponse.UserSummary::userId).toList());
        assertEquals("Member", first.getFirst().roleName());
        assertEquals("D1", first.getFirst().departmentId());
        assertNotNull(first.getFirst().createdAt());
    }

    @Test
    void combinesCaseInsensitiveUnicodeAndLiteralLikeFilters() {
        assertEquals(2, repository.countUsers(" NGUYỄN ", null, null));
        assertEquals(1, repository.countUsers("%_!", "active", "R_ADMIN"));
        assertEquals(List.of("U1"), repository.findUsers("%_!", "active", "R_ADMIN", 10, 0)
                .stream().map(UserListResponse.UserSummary::userId).toList());
        assertEquals(List.of("U4", "U3"), repository.findUsers("alice", "ACTIVE", "R_MEMBER", 10, 0)
                .stream().map(UserListResponse.UserSummary::userId).toList());
    }

    @Test
    void usesBoundParametersRatherThanTreatingInjectedTextAsSql() {
        assertEquals(0, repository.countUsers("' OR 1=1 --", null, null));
        assertTrue(repository.findById("missing").isEmpty());
        assertEquals("U1", repository.findById("U1").orElseThrow().userId());
    }

    private void insert(String id, String name, String email, String status, String roleId, int createdAt) {
        jdbc.update("INSERT INTO `USER` VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", id, name, email,
                "0900" + id, status, roleId, "D1", LocalDateTime.of(2026, 1, createdAt, 8, 0), "secret");
    }

    @Configuration
    static class Config {
        @org.springframework.context.annotation.Bean
        UserListRepository userListRepository(JdbcTemplate jdbcTemplate) {
            return new UserListRepository(jdbcTemplate);
        }
    }
}
