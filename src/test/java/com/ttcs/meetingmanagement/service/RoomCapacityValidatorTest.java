package com.ttcs.meetingmanagement.service;

import com.ttcs.meetingmanagement.model.MeetingParticipant;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RoomCapacityValidatorTest {

    @Test
    void countsOrganizerOnlyOnceAndExcludesInactiveStatuses() {
        MeetingParticipant organizer = participant("organizer", "ACCEPTED");
        MeetingParticipant accepted = participant("user-1", "ACCEPTED");
        MeetingParticipant duplicate = participant("user-1", "PENDING");
        MeetingParticipant declined = participant("user-2", "DECLINED");
        MeetingParticipant removed = participant("user-3", "REMOVED");

        assertEquals(2, RoomCapacityValidator.countDistinctActiveDatabaseAttendees(
                "organizer",
                List.of(organizer, accepted, duplicate, declined, removed)));
    }

    @Test
    void rejectsUnknownCapacity() {
        assertThrows(ResponseStatusException.class,
                () -> RoomCapacityValidator.validate(null, 1));
        assertThrows(ResponseStatusException.class,
                () -> RoomCapacityValidator.validate(0, 1));
    }

    @Test
    void rejectsOverCapacity() {
        assertThrows(ResponseStatusException.class,
                () -> RoomCapacityValidator.validate(1, 2));
    }

    private MeetingParticipant participant(String userId, String status) {
        MeetingParticipant participant = new MeetingParticipant();
        participant.setUserId(userId);
        participant.setStatus(status);
        return participant;
    }
}