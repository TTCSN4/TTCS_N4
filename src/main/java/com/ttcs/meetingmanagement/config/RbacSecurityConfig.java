
package com.ttcs.meetingmanagement.config;

import com.ttcs.meetingmanagement.rbac.RbacAccess;
import com.ttcs.meetingmanagement.security.MobileJwtAuthenticationConverter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class RbacSecurityConfig {

    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            RbacAccess rbacAccess,
            MobileJwtAuthenticationConverter converter
    ) throws Exception {

        return http
                // API requests use Bearer JWT only; CSRF does not apply to this stateless boundary.
                .securityMatcher("/api/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/admin/**")
                        .access((authentication, context) -> new AuthorizationDecision(
                                rbacAccess.isAdmin(authentication.get())))
                        .requestMatchers(HttpMethod.POST, "/api/meetings")
                        .access((authentication, context) -> new AuthorizationDecision(
                                rbacAccess.canBook(authentication.get())))
                        .requestMatchers(HttpMethod.POST, "/api/room-bookings")
                        .access((authentication, context) -> new AuthorizationDecision(
                                rbacAccess.canBook(authentication.get())))
                        .requestMatchers(HttpMethod.POST, "/api/meetings/suggestions")
                        .access((authentication, context) -> new AuthorizationDecision(
                                rbacAccess.canBook(authentication.get())))
                        .requestMatchers(HttpMethod.GET, "/api/rooms", "/api/rooms/**")
                        .access((authentication, context) -> new AuthorizationDecision(
                                rbacAccess.canAttend(authentication.get())))
                        .requestMatchers("/api/**").denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
                        jwt.jwtAuthenticationConverter(converter)))
                .build();
    }

    @Bean
    @Order(3)
    public SecurityFilterChain nonApiSecurityFilterChain(HttpSecurity http)
            throws Exception {

        // Keep Spring Security's default CSRF protection for non-API routes.
        // This boundary remains safe if session/cookie authentication is introduced there.
        return http
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }
}
