
package com.ttcs.meetingmanagement.calendar;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class GoogleCalendarClient {

    private final RestClient restClient;

    public GoogleCalendarClient(RestClient.Builder builder) {
        this.restClient = builder
                .baseUrl("https://www.googleapis.com/calendar/v3")
                .build();
    }

    // Tao su kien Google Calendar
    public String createEvent(
            String accessToken,
            CalendarEventData event) {

        validateToken(accessToken);

        GoogleEventResponse response = restClient.post()
                .uri("/calendars/primary/events")
                .headers(headers ->
                        headers.setBearerAuth(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(toPayload(event))
                .retrieve()
                .body(GoogleEventResponse.class);

        if (response == null
                || response.id() == null
                || response.id().isBlank()) {
            throw new IllegalStateException(
                    "Google Calendar did not return event ID");
        }

        return response.id();
    }

    // Cap nhat su kien da dong bo
    public void updateEvent(
            String accessToken,
            String eventId,
            CalendarEventData event) {

        validateToken(accessToken);
        validateEventId(eventId);

        restClient.patch()
                .uri("/calendars/primary/events/{eventId}",
                        eventId)
                .headers(headers ->
                        headers.setBearerAuth(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(toPayload(event))
                .retrieve()
                .toBodilessEntity();
    }

    // Xoa su kien khi cuoc hop bi huy
    public void deleteEvent(
            String accessToken,
            String eventId) {

        validateToken(accessToken);
        validateEventId(eventId);

        restClient.delete()
                .uri("/calendars/primary/events/{eventId}",
                        eventId)
                .headers(headers ->
                        headers.setBearerAuth(accessToken))
                .retrieve()
                .toBodilessEntity();
    }

    private Map<String, Object> toPayload(
            CalendarEventData event) {

        Map<String, Object> payload =
                new LinkedHashMap<>();

        payload.put("summary", event.title());

        if (event.description() != null) {
            payload.put("description", event.description());
        }

        if (event.location() != null) {
            payload.put("location", event.location());
        }

        payload.put("start", Map.of(
                "dateTime", event.startTime().toString()
        ));

        payload.put("end", Map.of(
                "dateTime", event.endTime().toString()
        ));

        return payload;
    }

    private void validateToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException(
                    "Google access token is required");
        }
    }

    private void validateEventId(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException(
                    "Google event ID is required");
        }
    }

    private record GoogleEventResponse(String id) {
    }
}
