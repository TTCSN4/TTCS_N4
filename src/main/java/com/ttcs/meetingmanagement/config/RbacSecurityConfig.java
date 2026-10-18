
package com.ttcs.meetingmanagement.config;

import com.ttcs.meetingmanagement.rbac.RbacAccess;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class RbacSecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            RbacAccess rbacAccess
    ) throws Exception {

        http.csrf(csrf -> csrf.disable());
        http.sessionManagement(session -> session.sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS));
        http.oauth2ResourceServer(oauth -> oauth.jwt(jwt -> {}));
        http.authorizeHttpRequests(auth -> auth

            .requestMatchers("/api/auth/login").permitAll()
            .requestMatchers("/api/users", "/api/users/**", "/api/roles", "/api/departments")
            .access((a,c) -> new AuthorizationDecision(rbacAccess.isAdmin(a.get())))
            .requestMatchers(HttpMethod.GET, "/api/equipment/**", "/api/meetings/**")
            .access((a,c) -> new AuthorizationDecision(rbacAccess.canAttend(a.get())))
            .requestMatchers("/api/equipment/bookings", "/api/equipment/bookings/**")
            .access((a,c) -> new AuthorizationDecision(rbacAccess.canBook(a.get())))
            .requestMatchers("/api/equipment", "/api/equipment/**")
            .access((a,c) -> new AuthorizationDecision(rbacAccess.isAdmin(a.get())))
            .requestMatchers(HttpMethod.PUT, "/api/meetings/**")
            .access((a,c) -> new AuthorizationDecision(rbacAccess.canBook(a.get())))
            .requestMatchers(HttpMethod.DELETE, "/api/meetings/**")
            .access((a,c) -> new AuthorizationDecision(rbacAccess.canBook(a.get())))
            // US20: Chi Admin duoc quan ly vai tro
            .requestMatchers("/api/admin/**")
            .access((authentication, context) ->
                new AuthorizationDecision(
                    rbacAccess.isAdmin(authentication.get())
                )
            )

            // Chi Admin hoac Nguoi dat lich duoc tao cuoc hop
            .requestMatchers(
                HttpMethod.POST,
                "/api/meetings"
            )
            .access((authentication, context) ->
                new AuthorizationDecision(
                    rbacAccess.canBook(authentication.get())
                )
            )

            // Chi Admin hoac Nguoi dat lich duoc dat phong
            .requestMatchers(
                HttpMethod.POST,
                "/api/room-bookings"
            )
            .access((authentication, context) ->
                new AuthorizationDecision(
                    rbacAccess.canBook(authentication.get())
                )
            )

            // Goi y lich hop danh cho nguoi co quyen dat lich
            .requestMatchers(
                HttpMethod.POST,
                "/api/meetings/suggestions"
            )
            .access((authentication, context) ->
                new AuthorizationDecision(
                    rbacAccess.canBook(authentication.get())
                )
            )

            // Doc thong tin phong theo vai tro duoc phep
            .requestMatchers(
                HttpMethod.GET,
                "/api/rooms",
                "/api/rooms/**"
            )
            .access((authentication, context) ->
                new AuthorizationDecision(
                    rbacAccess.canAttend(authentication.get())
                )
            )

            // Cac API chua co quy tac duoc tu choi
            // cho den khi nhom thong nhat phan quyen
            .requestMatchers("/api/**").denyAll()

            // Khong ap dat RBAC len tai nguyen web tinh
            .anyRequest().permitAll()
        );

        // Giu nguyen CSRF mac dinh.
        // Chua cau hinh boi vi module dang nhap chua co.

        return http.build();
    }
}
