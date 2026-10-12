
package com.ttcs.meetingmanagement.controller;

import com.ttcs.meetingmanagement.dto.QrCodeResponse;
import com.ttcs.meetingmanagement.service.QrCodeService;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;

@RestController
@RequestMapping("/api/meetings")
public class QrCodeController {

    private final QrCodeService qrCodeService;
    private final JdbcTemplate jdbcTemplate;
    private final String issuerSecret;

    public QrCodeController(
            QrCodeService qrCodeService,
            JdbcTemplate jdbcTemplate,
            @Value("${QR_ISSUER_SECRET:}") String issuerSecret) {

        this.qrCodeService = qrCodeService;
        this.jdbcTemplate = jdbcTemplate;
        this.issuerSecret = issuerSecret;
    }

    // ======================================
    // US27 - SINH QR CHO CUOC HOP
    // ======================================

    @GetMapping("/{meetingId}/qr")
    public ResponseEntity<QrCodeResponse> generateQr(
            @PathVariable String meetingId,
            @RequestHeader(
                    value = "X-QR-Issuer-Key",
                    required = false
            ) String issuerKey) {

        // 1. Kiem tra quyen sinh QR
        verifyIssuer(issuerKey);

        // 2. Kiem tra meetingId theo ERD
        if (meetingId == null
                || meetingId.isBlank()
                || meetingId.length() > 50) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid meeting ID"
            );
        }

        meetingId = meetingId.trim();

        // 3. Kiem tra cuoc hop co ton tai
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM meeting
                WHERE meeting_id = ?
                  AND UPPER(TRIM(status)) = 'SCHEDULED'
                """,
                Integer.class,
                meetingId
        );

        if (count == null || count == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Scheduled meeting not found"
            );
        }

        // 4. Sinh token QR co chu ky HMAC
        QrCodeService.IssuedQr qr =
                qrCodeService.generateToken(meetingId);

        // 5. Tao anh QR PNG
        String imageBase64 =
                createQrImage(qr.token());

        // 6. Tra ve cho frontend
        QrCodeResponse response = new QrCodeResponse(
                meetingId,
                qr.token(),
                qr.expiresAt(),
                imageBase64
        );

        return ResponseEntity
                .ok()
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

    // ======================================
    // KIEM TRA KHOA SINH QR
    // ======================================

    private void verifyIssuer(String providedKey) {

        if (issuerSecret == null
                || issuerSecret.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "QR issuer secret is not configured"
            );
        }

        if (providedKey == null) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "QR issuer key is required"
            );
        }

        boolean valid = MessageDigest.isEqual(
                issuerSecret.getBytes(StandardCharsets.UTF_8),
                providedKey.getBytes(StandardCharsets.UTF_8)
        );

        if (!valid) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Invalid QR issuer key"
            );
        }
    }

    // ======================================
    // TAO ANH QR PNG DANG BASE64
    // ======================================

    private String createQrImage(String token) {

        try {
            QRCodeWriter writer = new QRCodeWriter();

            var matrix = writer.encode(
                    token,
                    BarcodeFormat.QR_CODE,
                    350,
                    350,
                    Map.of(
                            EncodeHintType.CHARACTER_SET, "UTF-8",
                            EncodeHintType.MARGIN, 2
                    )
            );

            ByteArrayOutputStream output =
                    new ByteArrayOutputStream();

            MatrixToImageWriter.writeToStream(
                    matrix,
                    "PNG",
                    output
            );

            return Base64.getEncoder()
                    .encodeToString(output.toByteArray());

        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Cannot generate QR image",
                    ex
            );
        }
    }
}
