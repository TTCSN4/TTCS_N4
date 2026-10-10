
package com.ttcs.meetingmanagement.calendar;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@Service
public class GoogleCalendarOAuthService {

    private final OAuth2AuthorizedClientService authorizedClientService;

    public GoogleCalendarOAuthService(
            OAuth2AuthorizedClientService authorizedClientService) {
        this.authorizedClientService = authorizedClientService;
    }

    // Kiem tra nguoi dung da ket noi Google OAuth hay chua
    public boolean isConnected(Authentication authentication) {
        if (!(authentication instanceof OAuth2AuthenticationToken oauth)) {
            return false;
        }

        if (!"google".equals(oauth.getAuthorizedClientRegistrationId())) {
            return false;
        }

        OAuth2AuthorizedClient client =
                authorizedClientService.loadAuthorizedClient(
                        "google", authentication.getName());

        return client != null;
    }

    // Lay access token cua nguoi dung da dang nhap
    // Chi su dung trong Backend, khong tra token ve Frontend
    public String getAccessToken(Authentication authentication) {

        if (!isConnected(authentication)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Google Calendar account is not connected");
        }

        OAuth2AuthorizedClient client =
                authorizedClientService.loadAuthorizedClient(
                        "google", authentication.getName());

        Instant expiresAt = client.getAccessToken().getExpiresAt();

        if (expiresAt != null && !expiresAt.isAfter(Instant.now())) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Google access token expired");
        }

        return client.getAccessToken().getTokenValue();
    }
}
