
package com.ttcs.meetingmanagement.calendar;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class GoogleCalendarSyncService {

    private final GoogleCalendarClient googleCalendarClient;
    private final GoogleCalendarOAuthService oauthService;

    public GoogleCalendarSyncService(
            GoogleCalendarClient googleCalendarClient,
            GoogleCalendarOAuthService oauthService) {

        this.googleCalendarClient = googleCalendarClient;
        this.oauthService = oauthService;
    }

    // Tao su kien tren Google Calendar
    public String createEvent(
            Authentication authentication,
            CalendarEventData event) {

        String accessToken =
                oauthService.getAccessToken(authentication);

        return googleCalendarClient.createEvent(
                accessToken, event);
    }

    // Cap nhat su kien da ton tai
    public void updateEvent(
            Authentication authentication,
            String externalEventId,
            CalendarEventData event) {

        String accessToken =
                oauthService.getAccessToken(authentication);

        googleCalendarClient.updateEvent(
                accessToken,
                externalEventId,
                event);
    }

    // Xoa su kien khi cuoc hop bi huy
    public void deleteEvent(
            Authentication authentication,
            String externalEventId) {

        String accessToken =
                oauthService.getAccessToken(authentication);

        googleCalendarClient.deleteEvent(
                accessToken,
                externalEventId);
    }
}
