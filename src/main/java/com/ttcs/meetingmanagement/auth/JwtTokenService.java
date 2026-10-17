package com.ttcs.meetingmanagement.auth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.ttcs.meetingmanagement.model.User;
import com.ttcs.meetingmanagement.rbac.RoleCategory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.List;

@Service
public class JwtTokenService {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenService.class);

    private static final List<String> KNOWN_WEAK_SECRETS = List.of(
            "secret", "password", "123456", "admin", "jwtsecret", "meeting-management-secret",
            "dGhpc19pc19hX3NlY3VyZV9qd3Rfc2VjcmV0X2Zvcl9kZXZlbG9wbWVudF8yNTZiaXQ="
    );

    private final byte[] secretKeyBytes;
    private final String issuer;
    private final String audience;
    private final long expirationSeconds;

    public JwtTokenService(
            @Value("${app.jwt.secret:}")
            String secretKey,

            @Value("${app.jwt.issuer:meeting-management}")
            String issuer,

            @Value("${app.jwt.audience:meeting-management-client}")
            String audience,

            @Value("${app.jwt.expiration-seconds:86400}")
            long expirationSeconds,

            Environment environment
    ) {
        boolean isProduction = Arrays.asList(environment.getActiveProfiles()).contains("mysql")
                || Arrays.asList(environment.getActiveProfiles()).contains("prod")
                || Arrays.asList(environment.getActiveProfiles()).contains("production");

        this.secretKeyBytes = resolveKeyBytes(secretKey, isProduction);
        this.issuer = issuer;
        this.audience = audience;
        this.expirationSeconds = expirationSeconds;
    }

    private static byte[] resolveKeyBytes(String secret, boolean isProduction) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "CRITICAL SECURITY CONFIGURATION ERROR: JWT_SECRET environment variable is missing or empty. " +
                    "A 256-bit (minimum 32-byte) secret must be explicitly provided via the JWT_SECRET environment variable. " +
                    "Running with a missing or default secret is strictly prohibited.");
        }

        for (String weak : KNOWN_WEAK_SECRETS) {
            if (secret.equalsIgnoreCase(weak)) {
                throw new IllegalStateException(
                        "CRITICAL SECURITY CONFIGURATION ERROR: Known public/weak development JWT secret detected. " +
                        "A unique, cryptographically random secret of at least 256 bits (32 bytes) must be supplied. " +
                        "Note: Hashing a weak secret with SHA-256 is not acceptable as a substitute for true entropy.");
            }
        }

        // Validate secret strength: Minimum 256 bits (32 bytes) of genuine key material.
        // We DO NOT SHA-256 hash a short password, because hashing a weak secret does not provide real entropy.
        byte[] rawBytes = secret.getBytes(StandardCharsets.UTF_8);
        try {
            byte[] decoded = Base64.getDecoder().decode(secret);
            if (decoded.length >= 32) {
                return decoded;
            }
        } catch (IllegalArgumentException ignored) {
        }

        if (rawBytes.length < 32) {
            throw new IllegalArgumentException(
                    "CRITICAL SECURITY CONFIGURATION ERROR: JWT secret key is too short. " +
                    "It must contain at least 32 bytes (256 bits) of raw key material to resist brute-force attacks. " +
                    "SHA-256 hashing or padding of a short secret is prohibited. Current length: " + rawBytes.length + " bytes.");
        }

        return rawBytes;
    }

    public String generateToken(User user) {
        if (user == null || user.getUserId() == null) {
            throw new IllegalArgumentException("User cannot be null when generating JWT");
        }

        RoleCategory category = RoleCategory.categorize(user.getRole());
        String roleName = category != null ? category.name() : (user.getRole() != null ? user.getRole().getRoleName() : "MEMBER");

        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationSeconds * 1000);

        try {
            JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                    .subject(user.getUserId())
                    .issuer(issuer)
                    .audience(audience)
                    .issueTime(now)
                    .expirationTime(expiry)
                    .claim("email", user.getEmail())
                    .claim("name", user.getFullName())
                    .claim("role", roleName)
                    .claim("roles", List.of("ROLE_" + roleName))
                    .build();

            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader(JWSAlgorithm.HS256),
                    claimsSet
            );

            JWSSigner signer = new MACSigner(secretKeyBytes);
            signedJWT.sign(signer);

            return signedJWT.serialize();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate JWT token", e);
        }
    }

    public JWTClaimsSet parseAndValidate(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }

        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            JWSVerifier verifier = new MACVerifier(secretKeyBytes);

            if (!signedJWT.verify(verifier)) {
                return null;
            }

            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            Date now = new Date();

            if (claims.getExpirationTime() != null && claims.getExpirationTime().before(now)) {
                return null;
            }

            if (issuer != null && !issuer.isBlank() && !issuer.equals(claims.getIssuer())) {
                return null;
            }

            if (audience != null && !audience.isBlank() && claims.getAudience() != null
                    && !claims.getAudience().isEmpty() && !claims.getAudience().contains(audience)) {
                return null;
            }

            return claims;
        } catch (Exception e) {
            return null;
        }
    }

    public String validateTokenAndExtractUserId(String token) {
        JWTClaimsSet claims = parseAndValidate(token);
        return claims != null ? claims.getSubject() : null;
    }

    public byte[] getSecretKeyBytes() {
        return secretKeyBytes.clone();
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }
}
