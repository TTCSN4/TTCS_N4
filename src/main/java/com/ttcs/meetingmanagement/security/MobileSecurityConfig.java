package com.ttcs.meetingmanagement.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;

import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

@Configuration
@Order(1)
public class MobileSecurityConfig {

    private static final List<String> KNOWN_WEAK_SECRETS = List.of(
            "secret", "password", "123456", "admin", "jwtsecret", "meeting-management-secret",
            "dGhpc19pc19hX3NlY3VyZV9qd3Rfc2VjcmV0X2Zvcl9kZXZlbG9wbWVudF8yNTZiaXQ="
    );

    private final String secretKeyString;
    private final String issuer;
    private final String audience;
    private final Environment environment;

    public MobileSecurityConfig(
            @Value("${app.mobile.jwt.secret:}")
            String secretKeyString,

            @Value("${app.mobile.jwt.issuer:meeting-management-mobile}")
            String issuer,

            @Value("${app.mobile.jwt.audience:mobile-app}")
            String audience,

            Environment environment
    ) {
        this.secretKeyString = secretKeyString;
        this.issuer = issuer;
        this.audience = audience;
        this.environment = environment;
    }

    private byte[] resolveMobileKey() {
        if (secretKeyString == null || secretKeyString.isBlank()) {
            throw new IllegalStateException(
                    "CRITICAL SECURITY CONFIGURATION ERROR: MOBILE_JWT_SECRET environment variable is missing or empty. " +
                    "A 256-bit (minimum 32-byte) secret must be explicitly provided via the MOBILE_JWT_SECRET environment variable. " +
                    "Running with a missing or default secret is strictly prohibited.");
        }

        for (String weak : KNOWN_WEAK_SECRETS) {
            if (secretKeyString.equalsIgnoreCase(weak)) {
                throw new IllegalStateException(
                        "CRITICAL SECURITY CONFIGURATION ERROR: Known public/weak mobile JWT secret detected. " +
                        "A unique, cryptographically random secret of at least 256 bits (32 bytes) must be supplied. " +
                        "Note: Zero-padding or hashing weak keys is not permitted.");
            }
        }

        byte[] key;
        try {
            key = Base64.getDecoder().decode(secretKeyString);
        } catch (IllegalArgumentException ex) {
            key = secretKeyString.getBytes(StandardCharsets.UTF_8);
        }

        if (key.length < 32) {
            throw new IllegalArgumentException(
                    "Mobile JWT secret key is too short. It must contain at least 32 bytes (256 bits) of raw key material. " +
                    "Zero-padding or hashing weak keys is not permitted. Current length: " + key.length + " bytes.");
        }

        return key;
    }

    // ========================================
    // 1. XAC THUC JWT TOKEN CHO MOBILE
    // ========================================
    @Bean
    public JwtDecoder jwtDecoder() {
        byte[] key = resolveMobileKey();

        SecretKeySpec secretKey = new SecretKeySpec(
                key,
                "HmacSHA256"
        );

        NimbusJwtDecoder decoder =
                NimbusJwtDecoder
                        .withSecretKey(secretKey)
                        .macAlgorithm(MacAlgorithm.HS256)
                        .build();

        // KIEM TRA ISSUER VA HAN SU DUNG TOKEN
        OAuth2TokenValidator<Jwt> defaultValidator =
                JwtValidators.createDefaultWithIssuer(issuer);

        // KIEM TRA AUDIENCE
        OAuth2TokenValidator<Jwt> audienceValidator =
                new JwtClaimValidator<List<String>>(
                        "aud",
                        aud -> aud != null && aud.contains(audience)
                );

        // KIEM TRA USER ID TRONG JWT
        OAuth2TokenValidator<Jwt> subjectValidator =
                new JwtClaimValidator<String>(
                        "sub",
                        sub -> sub != null && !sub.isBlank()
                );

        // KET HOP CAC BO KIEM TRA JWT
        OAuth2TokenValidator<Jwt> validators =
                new DelegatingOAuth2TokenValidator<>(
                        defaultValidator,
                        audienceValidator,
                        subjectValidator
                );

        decoder.setJwtValidator(validators);

        return decoder;
    }

    // ========================================
    // 2. CAU HINH BAO MAT API MOBILE (ORDER 1)
    // ========================================
    @Bean
    @Order(1)
    public SecurityFilterChain mobileFilterChain(
            HttpSecurity http,
            MobileJwtAuthenticationConverter converter
    ) throws Exception {

        return http
                // GIOI HAN PHAM VI CHI CHO MOBILE API VA GIAO DIEN US17
                .securityMatcher("/api/mobile/**", "/us17.html")

                // KHONG SU DUNG CSRF CHO API STATELESS
                .csrf(csrf -> csrf.disable())

                // KHONG TAO HTTP SESSION
                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                // PHAN QUYEN TRUY CAP
                .authorizeHttpRequests(auth -> auth

                        // CHO PHEP MO GIAO DIEN HTML
                        .requestMatchers(
                                HttpMethod.GET,
                                "/us17.html"
                        )
                        .permitAll()

                        // API LICH HOP BAT BUOC CO JWT
                        .requestMatchers(
                                "/api/mobile/**"
                        )
                        .authenticated()

                        // TU CHOI CAC REQUEST KHAC TRONG PHAM VI MOBILE
                        .anyRequest()
                        .denyAll()
                )

                // XAC THUC JWT BEARER TOKEN
                .oauth2ResourceServer(oauth2 ->
                        oauth2.jwt(jwt ->
                                jwt.jwtAuthenticationConverter(
                                        converter
                                )
                        )
                )

                .build();
    }
}
