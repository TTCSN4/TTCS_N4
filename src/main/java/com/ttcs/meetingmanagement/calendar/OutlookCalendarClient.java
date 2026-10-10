
package com.ttcs.meetingmanagement.calendar;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OutlookCalendarClient {

    private final RestClient restClient;

    public OutlookCalendarClient(RestClient.Builder builder) {
        this.restClient = builder
                .baseUrl("https://graph.microsoft.com/v1.0")
                .build();
    }

    // Tao su kien trong Outlook Calendar
    public String createEvent(
            String accessToken,
            CalendarEventData event) {

        validateToken(accessToken);

        OutlookEventResponse response = restClient.post()
                .uri("/me/calendar/events")
                .headers(headers ->
                        headers.setBearerAuth(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(toPayload(event))
                .retrieve()
                .body(OutlookEventResponse.class);

        if (response == null
                || response.id() == null
                || response.id().isBlank()) {
            throw new IllegalStateException(
                    "Outlook did not return event ID");
        }

        return response.id();
    }

    // Cap nhat su kien trong Outlook
    public void updateEvent(
            String accessToken,
            String externalEventId,
            CalendarEventData event) {

        validateToken(accessToken);
        validateEventId(externalEventId);

        restClient.patch()
                .uri("/me/events/{eventId}", externalEventId)
                .headers(headers ->
                        headers.setBearerAuth(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(toPayload(event))
                .retrieve()
                .toBodilessEntity();
    }

    // Xoa su kien khoi Outlook
    public void deleteEvent(
            String accessToken,
            String externalEventId) {

        validateToken(accessToken);
        validateEventId(externalEventId);

        restClient.delete()
                .uri("/me/events/{eventId}", externalEventId)
                .headers(headers ->
                        headers.setBearerAuth(accessToken))
                .retrieve()
                .toBodilessEntity();
    }

    private Map<String, Object> toPayload(
            CalendarEventData event) {

        Map<String, Object> payload =
                new LinkedHashMap<>();

        payload.put("subject", event.title());

        payload.put("body", Map.of(
                "contentType", "Text",
                "content", event.description() == null
                        ? "" : event.description()
        ));

        if (event.location() != null) {
            payload.put("location", Map.of(
                    "displayName", event.location()
            ));
        }

        payload.put("start", toTime(event.startTime()));
        payload.put("end", toTime(event.endTime()));

        return payload;
    }

    private Map<String, String> toTime(
            OffsetDateTime dateTime) {

        String utcTime = dateTime
                .withOffsetSameInstant(ZoneOffset.UTC)
                .toLocalDateTime()
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        return Map.of(
                "dateTime", utcTime,
                "timeZone", "UTC"
        );
    }

    private void validateToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException(
                    "Microsoft access token is required");
        }
    }

    private void validateEventId(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException(
                    "Outlook event ID is required");
        }
    }

    private record OutlookEventResponse(String id) {
    }
}
