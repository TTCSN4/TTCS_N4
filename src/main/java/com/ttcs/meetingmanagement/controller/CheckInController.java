
package com.ttcs.meetingmanagement.controller;

import com.ttcs.meetingmanagement.dto.CheckInRequest;
import com.ttcs.meetingmanagement.dto.CheckOutRequest;
import com.ttcs.meetingmanagement.dto.CheckInResponse;
import com.ttcs.meetingmanagement.service.CheckInService;

import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.util.List;

@RestController
@RequestMapping("/api")
public class CheckInController {

    private final CheckInService checkInService;
    private final JdbcTemplate jdbc;
    private final String issuerSecret;

    public CheckInController(
            CheckInService checkInService,
            JdbcTemplate jdbc,
            @Value("${QR_ISSUER_SECRET:}") String issuerSecret) {

        this.checkInService = checkInService;
        this.jdbc = jdbc;
        this.issuerSecret = issuerSecret;
    }

    // ======================================
    // US27 - CHECK-IN
    // ======================================

    @PostMapping("/check-in")
    public ResponseEntity<CheckInResponse> checkIn(
            @Valid @RequestBody CheckInRequest request) {

        CheckInResponse response =
                checkInService.checkIn(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

    // ======================================
    // US27 - CHECK-OUT
    // ======================================

    @PostMapping("/check-out")
    public ResponseEntity<CheckInResponse> checkOut(
            @Valid @RequestBody CheckOutRequest request) {

        CheckInResponse response =
                checkInService.checkOut(request);

        return ResponseEntity
                .ok()
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

    // ======================================
    // US27 - LICH SU CHECK-IN / CHECK-OUT
    // ======================================

    @GetMapping("/meetings/{meetingId}/check-in-logs")
    public ResponseEntity<List<CheckInResponse>> getHistory(
            @PathVariable String meetingId,
            @RequestHeader(
                    value = "X-QR-Issuer-Key",
                    required = false
            ) String issuerKey) {

        // Chi cho client noi bo duoc cap quyen xem log
        verifyIssuer(issuerKey);

        if (meetingId == null
                || meetingId.isBlank()
                || meetingId.length() > 50) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid meeting ID"
            );
        }

        String sql = """
                SELECT
                    log_id,
                    meeting_id,
                    user_id,
                    check_in_time,
                    check_out_time
                FROM check_in_log
                WHERE meeting_id = ?
                ORDER BY check_in_time DESC
                """;

        List<CheckInResponse> history = jdbc.query(
                sql,
                (rs, rowNum) -> {

                    Timestamp checkout =
                            rs.getTimestamp("check_out_time");

                    return new CheckInResponse(
                            rs.getString("log_id"),
                            rs.getString("meeting_id"),
                            rs.getString("user_id"),
                            rs.getTimestamp("check_in_time")
                                    .toLocalDateTime(),
                            checkout == null
                                    ? null
                                    : checkout.toLocalDateTime(),
                            checkout == null
                                    ? "CHECKED_IN"
                                    : "CHECKED_OUT"
                    );
                },
                meetingId.trim()
        );

        return ResponseEntity
                .ok()
                .cacheControl(CacheControl.noStore())
                .body(history);
    }

    // Kiem tra khoa truy cap API quan tri noi bo
    private void verifyIssuer(String providedKey) {

        if (issuerSecret == null || issuerSecret.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "QR issuer secret is not configured"
            );
        }

        if (providedKey == null
                || !MessageDigest.isEqual(
                        issuerSecret.getBytes(StandardCharsets.UTF_8),
                        providedKey.getBytes(StandardCharsets.UTF_8))) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Invalid issuer key"
            );
        }
    }
}
