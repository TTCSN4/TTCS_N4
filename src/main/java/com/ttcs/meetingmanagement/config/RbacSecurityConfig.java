package com.ttcs.meetingmanagement.config;

import com.ttcs.meetingmanagement.auth.JwtAuthFilter;
import com.ttcs.meetingmanagement.rbac.RbacAccess;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
@Order(2)
public class RbacSecurityConfig {

    private final RbacAccess rbacAccess;
    private final JwtAuthFilter jwtAuthFilter;

    public RbacSecurityConfig(
            RbacAccess rbacAccess,
            JwtAuthFilter jwtAuthFilter) {
        this.rbacAccess = rbacAccess;
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
            // 1. TAT CSRF CHO API STATELESS
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/**")
            )

            // 2. PHIEN STATELESS CHO REST API
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // 3. XU LY RO RANG 401 UNAUTHORIZED VA 403 FORBIDDEN
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"status\":403,\"error\":\"Forbidden\",\"message\":\"Access denied\"}");
                })
            )

            // 4. PHAN QUYEN TRUY CAP HE THONG
            .authorizeHttpRequests(auth -> auth

                // AUTHENTICATION APIs (Public)
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/auth/login",
                    "/api/login"
                ).permitAll()

                // THONG TIN NGUOI DUNG HIEN TAI (Can xac thuc)
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/auth/me",
                    "/api/me"
                ).access((authentication, context) ->
                    new AuthorizationDecision(
                        rbacAccess.canAttend(authentication.get())
                    )
                )

                // ADMIN ONLY: Quan ly vai tro, bao cao quan tri, HRM integration
                .requestMatchers(
                    "/api/admin/**",
                    "/api/integrations/hrm/**"
                ).access((authentication, context) ->
                    new AuthorizationDecision(
                        rbacAccess.isAdmin(authentication.get())
                    )
                )

                // US18: Tao tai khoan nguoi dung (Admin only)
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/users"
                ).access((authentication, context) ->
                    new AuthorizationDecision(
                        rbacAccess.isAdmin(authentication.get())
                    )
                )

                // US19: Xem danh sach nguoi dung (Admin va PM)
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/users"
                ).access((authentication, context) ->
                    new AuthorizationDecision(
                        rbacAccess.canBook(authentication.get())
                    )
                )

                // Xem danh muc vai tro va phong ban (Nguoi dung hop le)
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/roles",
                    "/api/departments"
                ).access((authentication, context) ->
                    new AuthorizationDecision(
                        rbacAccess.canAttend(authentication.get())
                    )
                )

                // US11: Them, sua, xoa phong (Admin only)
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/rooms",
                    "/api/rooms/**"
                ).access((authentication, context) ->
                    new AuthorizationDecision(
                        rbacAccess.isAdmin(authentication.get())
                    )
                )
                .requestMatchers(
                    HttpMethod.PUT,
                    "/api/rooms/**"
                ).access((authentication, context) ->
                    new AuthorizationDecision(
                        rbacAccess.isAdmin(authentication.get())
                    )
                )
                .requestMatchers(
                    HttpMethod.DELETE,
                    "/api/rooms/**"
                ).access((authentication, context) ->
                    new AuthorizationDecision(
                        rbacAccess.isAdmin(authentication.get())
                    )
                )

                // Doc thong tin phong (Admin, PM, Member)
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/rooms",
                    "/api/rooms/**"
                ).access((authentication, context) ->
                    new AuthorizationDecision(
                        rbacAccess.canAttend(authentication.get())
                    )
                )

                // US14: Them, sua, xoa thiet bi (Admin only)
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/equipment",
                    "/api/equipment/**"
                ).access((authentication, context) ->
                    new AuthorizationDecision(
                        rbacAccess.isAdmin(authentication.get())
                    )
                )
                .requestMatchers(
                    HttpMethod.PUT,
                    "/api/equipment/**"
                ).access((authentication, context) ->
                    new AuthorizationDecision(
                        rbacAccess.isAdmin(authentication.get())
                    )
                )
                .requestMatchers(
                    HttpMethod.DELETE,
                    "/api/equipment/**"
                ).access((authentication, context) ->
                    new AuthorizationDecision(
                        rbacAccess.isAdmin(authentication.get())
                    )
                )

                // Doc thong tin thiet bi (Admin, PM, Member)
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/equipment",
                    "/api/equipment/**"
                ).access((authentication, context) ->
                    new AuthorizationDecision(
                        rbacAccess.canAttend(authentication.get())
                    )
                )

                // US01 + US08 + US21: Tao cuoc hop, lich lap, dat phong (Admin va PM)
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/meetings",
                    "/api/meetings/recurring",
                    "/api/room-bookings"
                ).access((authentication, context) ->
                    new AuthorizationDecision(
                        rbacAccess.canBook(authentication.get())
                    )
                )

                // Goi y lich hop (Admin, PM, Member)
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/meetings/suggestions"
                ).access((authentication, context) ->
                    new AuthorizationDecision(
                        rbacAccess.canAttend(authentication.get())
                    )
                )

                // Doc danh sach va chi tiet cuoc hop (Admin, PM, Member)
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/meetings",
                    "/api/meetings/**"
                ).access((authentication, context) ->
                    new AuthorizationDecision(
                        rbacAccess.canAttend(authentication.get())
                    )
                )

                // US02 + US09: Sua, huy cuoc hop / dat phong (Admin va PM - Service kiem tra organizer)
                .requestMatchers(
                    HttpMethod.PUT,
                    "/api/meetings/**"
                ).access((authentication, context) ->
                    new AuthorizationDecision(
                        rbacAccess.canBook(authentication.get())
                    )
                )
                .requestMatchers(
                    HttpMethod.DELETE,
                    "/api/meetings/**",
                    "/api/room-bookings/**"
                ).access((authentication, context) ->
                    new AuthorizationDecision(
                        rbacAccess.canBook(authentication.get())
                    )
                )

                // Bao cao cuoc hop (Admin va PM)
                .requestMatchers(
                    "/api/reports/**"
                ).access((authentication, context) ->
                    new AuthorizationDecision(
                        rbacAccess.canBook(authentication.get())
                    )
                )

                // Chatbot, thong bao, tinh trang san sang, diem danh (Nguoi dung hop le)
                .requestMatchers(
                    "/api/chatbot/**",
                    "/api/availability/**",
                    "/api/check-in/**"
                ).access((authentication, context) ->
                    new AuthorizationDecision(
                        rbacAccess.canAttend(authentication.get())
                    )
                )

                // MAC DINH TU CHOI CAC API KHAC CHUA KHAI BAO QUYEN
                .requestMatchers("/api/**").denyAll()

                // TAI NGUYEN TINH VA GIAO DIEN HTML
                .anyRequest().permitAll()
            )

            // DANG KY BO LOC JWT AUTHENTICATION
            .addFilterBefore(
                jwtAuthFilter,
                UsernamePasswordAuthenticationFilter.class
            );

        return http.build();
    }
}
