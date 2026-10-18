package com.ttcs.meetingmanagement;

import com.ttcs.meetingmanagement.dto.RecurringMeetingRequest;
import com.ttcs.meetingmanagement.dto.RecurringMeetingResponse;
import com.ttcs.meetingmanagement.model.Role;
import com.ttcs.meetingmanagement.model.User;
import com.ttcs.meetingmanagement.recurrence.RecurrenceFrequency;
import com.ttcs.meetingmanagement.recurrence.RecurringMeetingService;
import com.ttcs.meetingmanagement.repository.MeetingRepository;
import com.ttcs.meetingmanagement.repository.RoleRepository;
import com.ttcs.meetingmanagement.repository.UserRepository;
import com.ttcs.meetingmanagement.room.Room;
import com.ttcs.meetingmanagement.room.RoomRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class RecurringPersistenceTest {

    @Autowired
    private RecurringMeetingService service;

    @Autowired
    private MeetingRepository meetings;

    @Autowired
    private RoomRepository rooms;

    @Autowired
    private UserRepository users;

    @Autowired
    private RoleRepository roles;

    @Test
    void createsPersistedSeriesAndRejectsRoomConflict() {
        Room room = new Room();
        room.setRoomId("US03-test");
        room.setRoomName("Test room");
        room.setCapacity(8);
        room.setStatus("AVAILABLE");
        rooms.saveAndFlush(room);

        Role role = roles.findById("ROLE_ADMIN").orElseGet(() ->
                roles.saveAndFlush(new Role("ROLE_ADMIN", "ADMIN")));

        User user = users.findById("USER_REC_TEST").orElseGet(() -> {
            User u = new User();
            u.setUserId("USER_REC_TEST");
            u.setFullName("Recurring User");
            u.setEmail("recurring@example.com");
            u.setStatus("ACTIVE");
            u.setRole(role);
            return users.saveAndFlush(u);
        });

        RecurringMeetingRequest request = new RecurringMeetingRequest(
                user.getUserId(),
                room.getRoomId(),
                "US03 integration",
                "Description",
                OffsetDateTime.parse("2099-01-05T09:00:00+07:00"),
                OffsetDateTime.parse("2099-01-05T10:00:00+07:00"),
                RecurrenceFrequency.WEEKLY,
                2,
                4
        );

        RecurringMeetingResponse result = service.create(request);
        meetings.flush();

        assertEquals(4, result.createdCount());
        assertEquals(4, result.meetingIds().size());

        for (String id : result.meetingIds()) {
            var m = meetings.findById(id).orElseThrow();
            assertEquals(result.recurrenceSeriesId(), m.getRecurrenceSeriesId());
            assertEquals(room.getRoomId(), m.getRoomId());
        }

        assertThrows(ResponseStatusException.class, () -> service.create(request));
    }
}
