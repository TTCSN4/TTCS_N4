package com.ttcs.meetingmanagement.auth;

import com.nimbusds.jwt.JWTClaimsSet;
import com.ttcs.meetingmanagement.model.Role;
import com.ttcs.meetingmanagement.model.User;
import com.ttcs.meetingmanagement.rbac.AuthenticatedUserPrincipal;
import com.ttcs.meetingmanagement.rbac.RoleCategory;
import com.ttcs.meetingmanagement.repository.UserRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;
    private final UserRepository userRepository;

    public JwtAuthFilter(JwtTokenService jwtTokenService, UserRepository userRepository) {
        this.jwtTokenService = jwtTokenService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();

            JWTClaimsSet claims = jwtTokenService.parseAndValidate(token);
            if (claims != null) {
                String userId = claims.getSubject();

                if (userId != null && !userId.isBlank()) {
                    Optional<User> userOpt = userRepository.findWithRole(userId.trim());

                    if (userOpt.isPresent()) {
                        User user = userOpt.get();

                        // Enforce active status and valid role in database
                        if ("ACTIVE".equalsIgnoreCase(user.getStatus()) && user.getRole() != null) {
                            Role role = user.getRole();
                            RoleCategory category = RoleCategory.categorize(role);

                            if (category != null) {
                                AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(
                                        user.getUserId(),
                                        user.getEmail(),
                                        user.getFullName(),
                                        role.getRoleId(),
                                        role.getRoleName(),
                                        category
                                );

                                List<SimpleGrantedAuthority> authorities = List.of(
                                        new SimpleGrantedAuthority("ROLE_" + category.name())
                                );

                                UsernamePasswordAuthenticationToken authentication =
                                        new UsernamePasswordAuthenticationToken(
                                                principal,
                                                null,
                                                authorities
                                        );

                                SecurityContextHolder.getContext().setAuthentication(authentication);
                            }
                        }
                    }
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
