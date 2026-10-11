
package com.ttcs.meetingmanagement.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
public class QrCodeService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final long QR_VALID_SECONDS = 900; // 15 phut

    private final String secret;

    public QrCodeService(
            @Value("${QR_HMAC_SECRET:}") String secret) {
        this.secret = secret;
    }

    // ======================================
    // 1. SINH QR TOKEN CHO CUOC HOP
    // ======================================

    public IssuedQr generateToken(String meetingId) {

        if (meetingId == null
                || meetingId.isBlank()
                || meetingId.length() > 50
                || meetingId.contains("\n")) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid meeting ID"
            );
        }

        Instant expiresAt = Instant.now()
                .plusSeconds(QR_VALID_SECONDS);

        // Them ma ngau nhien de moi QR la duy nhat
        String payload = meetingId.trim()
                + "\n"
                + expiresAt.getEpochSecond()
                + "\n"
                + UUID.randomUUID();

        String encodedPayload = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                        payload.getBytes(StandardCharsets.UTF_8)
                );

        String signedData = "v1." + encodedPayload;

        String signature = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(sign(signedData));

        String token = signedData + "." + signature;

        return new IssuedQr(token, expiresAt);
    }

    // ======================================
    // 2. XAC MINH QR TOKEN
    // ======================================

    public String verifyToken(String token) {

        if (token == null
                || token.isBlank()
                || token.length() > 4096) {

            throw invalidQr();
        }

        String[] parts = token.split("\\.", -1);

        if (parts.length != 3 || !"v1".equals(parts[0])) {
            throw invalidQr();
        }

        try {
            String signedData = parts[0] + "." + parts[1];

            byte[] expectedSignature = sign(signedData);

            byte[] receivedSignature =
                    Base64.getUrlDecoder().decode(parts[2]);

            // So sanh chu ky an toan
            if (!MessageDigest.isEqual(
                    expectedSignature,
                    receivedSignature)) {

                throw invalidQr();
            }

            String payload = new String(
                    Base64.getUrlDecoder().decode(parts[1]),
                    StandardCharsets.UTF_8
            );

            String[] fields = payload.split("\n", -1);

            if (fields.length != 3
                    || fields[0].isBlank()
                    || fields[0].length() > 50) {
                throw invalidQr();
            }

            String meetingId = fields[0];

            long expiresAt = Long.parseLong(fields[1]);

            if (Instant.now().getEpochSecond() >= expiresAt) {
                throw invalidQr();
            }

            return meetingId;

        } catch (IllegalArgumentException ex) {
            throw invalidQr();
        }
    }

    // ======================================
    // 3. TAO CHU KY HMAC SHA256
    // ======================================

    private byte[] sign(String data) {

        try {
            byte[] secretBytes = getSecretBytes();

            Mac mac = Mac.getInstance(HMAC_ALGORITHM);

            mac.init(new SecretKeySpec(
                    secretBytes,
                    HMAC_ALGORITHM
            ));

            return mac.doFinal(
                    data.getBytes(StandardCharsets.UTF_8)
            );

        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException(
                    "Cannot sign QR token", ex
            );
        }
    }

    // ======================================
    // 4. DOC KHOA BI MAT TU BIEN MOI TRUONG
    // ======================================

    private byte[] getSecretBytes() {

        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "QR_HMAC_SECRET is not configured"
            );
        }

        byte[] key;

        try {
            key = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "QR_HMAC_SECRET must be Base64", ex
            );
        }

        if (key.length < 32) {
            throw new IllegalStateException(
                    "QR_HMAC_SECRET must contain at least 32 bytes"
            );
        }

        return key;
    }

    private ResponseStatusException invalidQr() {
        return new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Invalid or expired QR token"
        );
    }

    // ======================================
    // 5. DU LIEU TRA VE SAU KHI SINH TOKEN
    // ======================================

    public record IssuedQr(
            String token,
            Instant expiresAt
    ) {
    }
}
