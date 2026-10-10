
package com.ttcs.meetingmanagement.controller;

import com.ttcs.meetingmanagement.calendar.GoogleCalendarOAuthService;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/calendar/google")
public class GoogleCalendarConnectionController {

    private final GoogleCalendarOAuthService oauthService;

    public GoogleCalendarConnectionController(
            GoogleCalendarOAuthService oauthService) {
        this.oauthService = oauthService;
    }

    @GetMapping("/status")
    public Map<String, Object> getStatus(Authentication authentication) {

        return Map.of(
                "provider", "GOOGLE",
                "connected", oauthService.isConnected(authentication)
        );
    }
}
