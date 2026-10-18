
package com.ttcs.meetingmanagement.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MobileJwtAuthenticationConverter
        implements Converter<Jwt, AbstractAuthenticationToken> {

    private final com.ttcs.meetingmanagement.repository.UserRepository users;
    public MobileJwtAuthenticationConverter(com.ttcs.meetingmanagement.repository.UserRepository users){this.users=users;}
    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {

        if(!users.findById(jwt.getSubject()).map(u -> "ACTIVE".equalsIgnoreCase(u.getStatus())).orElse(false))
            throw new org.springframework.security.authentication.DisabledException("Account is not active");
        // DANH TINH DUOC LAY TU TOKEN DA XAC THUC
        return new JwtAuthenticationToken(
                jwt,
                List.of(),
                jwt.getSubject()
        );
    }
}
