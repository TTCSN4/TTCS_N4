package com.ttcs.meetingmanagement.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.ttcs.meetingmanagement.model.MeetingParticipant;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/** Shared US10 rule for every flow that books a meeting room. */
public final class RoomCapacityValidator {

    private RoomCapacityValidator() {
    }

    public static void validate(Integer capacity, int totalParticipants) {
        if (capacity == null || capacity <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Room capacity is not configured"
            );
        }
        if (totalParticipants < 1 || totalParticipants > capacity) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Room capacity is " + capacity
                            + ", but the meeting has "
                            + totalParticipants + " participants"
            );
        }
    }

    public static int countDistinctActiveAttendees(
            String organizerId,
            Collection<String> participantIds) {
        Set<String> attendees = new HashSet<>();
        if (organizerId != null && !organizerId.isBlank()) {
            attendees.add(organizerId.trim());
        }
        if (participantIds != null) {
            participantIds.stream()
                    .filter(id -> id != null && !id.isBlank())
                    .map(String::trim)
                    .forEach(attendees::add);
        }
        return attendees.size();
    }

    public static int countDistinctActiveDatabaseAttendees(
            String organizerId,
            Collection<MeetingParticipant> participants) {
        Set<String> attendees = new HashSet<>();
        if (organizerId != null && !organizerId.isBlank()) {
            attendees.add(organizerId.trim());
        }
        if (participants != null) {
            participants.stream()
                    .filter(p -> p != null
                            && p.getUserId() != null
                            && (p.getStatus() == null
                            || !Set.of("DECLINED", "REMOVED")
                                    .contains(p.getStatus().toUpperCase())))
                    .map(MeetingParticipant::getUserId)
                    .map(String::trim)
                    .forEach(attendees::add);
        }
        return attendees.size();
    }
}