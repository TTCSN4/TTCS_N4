
package com.ttcs.meetingmanagement.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
import java.util.Base64;
import java.util.List;

@Configuration
public class MobileSecurityConfig {

    private final String secretBase64;
    private final String issuer;
    private final String audience;

    // DOC CAU HINH JWT TU APPLICATION.PROPERTIES
    public MobileSecurityConfig(
            @Value("${app.mobile.jwt.secret}")
            String secretBase64,

            @Value("${app.mobile.jwt.issuer}")
            String issuer,

            @Value("${app.mobile.jwt.audience}")
            String audience
    ) {
        this.secretBase64 = secretBase64;
        this.issuer = issuer;
        this.audience = audience;
    }

    // ========================================
    // 1. XAC THUC JWT TOKEN
    // ========================================
    @Bean
    public JwtDecoder jwtDecoder() {

        byte[] key;

        try {
            key = Base64.getDecoder()
                    .decode(secretBase64);

        } catch (IllegalArgumentException ex) {

            throw new IllegalArgumentException(
                    "MOBILE_JWT_SECRET phai la Base64 hop le",
                    ex
            );
        }

        if (key.length < 32) {
            throw new IllegalArgumentException(
                    "MOBILE_JWT_SECRET can it nhat 32 byte"
            );
        }

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
                        aud -> aud != null
                                && aud.contains(audience)
                );

        // KIEM TRA USER ID TRONG JWT
        OAuth2TokenValidator<Jwt> subjectValidator =
                new JwtClaimValidator<String>(
                        "sub",
                        sub -> sub != null
                                && !sub.isBlank()
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
    // 2. CAU HINH BAO MAT API MOBILE
    // ========================================
    @Bean
    public SecurityFilterChain mobileFilterChain(
            HttpSecurity http,
            MobileJwtAuthenticationConverter converter
    ) throws Exception {

        return http

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
                        // KHONG CAN TOKEN KHI TAI TRANG
                        .requestMatchers(
                                HttpMethod.GET,
                                "/us17.html"
                        )
                        .permitAll()

                        // API LICH HOP BAT BUOC CO JWT
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/mobile/calendar",
                                "/api/mobile/calendar/**"
                        )
                        .authenticated()

                        // TU CHOI CAC REQUEST KHAC
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
