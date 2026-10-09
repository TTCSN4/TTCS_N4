
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

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {

        // DANH TINH DUOC LAY TU TOKEN DA XAC THUC
        return new JwtAuthenticationToken(
                jwt,
                List.of(),
                jwt.getSubject()
        );
    }
}
