package com.ttcs.meetingmanagement.config;

import com.ttcs.meetingmanagement.rbac.RbacAccess;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

@Configuration
@EnableMethodSecurity
public class RbacSecurityConfig {

    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            RbacAccess rbacAccess
    ) throws Exception {

        http
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            )
            .authorizeHttpRequests(auth -> auth

                // US20: Chi Admin duoc quan ly vai tro
                .requestMatchers("/api/admin/**")
                .access((authentication, context) ->
                    new AuthorizationDecision(
                        rbacAccess.isAdmin(authentication.get())
                    )
                )

                // US11: Chi Admin duoc them/sua/xoa phong
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/rooms",
                    "/api/rooms/**"
                )
                .access((authentication, context) ->
                    new AuthorizationDecision(
                        rbacAccess.isAdmin(authentication.get())
                    )
                )
                .requestMatchers(
                    HttpMethod.PUT,
                    "/api/rooms/**"
                )
                .access((authentication, context) ->
                    new AuthorizationDecision(
                        rbacAccess.isAdmin(authentication.get())
                    )
                )
                .requestMatchers(
                    HttpMethod.DELETE,
                    "/api/rooms/**"
                )
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

        return http.build();
    }
}