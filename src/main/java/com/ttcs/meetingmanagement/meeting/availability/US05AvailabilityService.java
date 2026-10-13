package com.ttcs.meetingmanagement.meeting.availability;

import com.ttcs.meetingmanagement.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static com.ttcs.meetingmanagement.meeting.availability.US05AvailabilityDtos.*;

@Service
@Transactional(readOnly = true)
public class US05AvailabilityService {
    private static final LocalTime WORK_START = LocalTime.of(8, 0), WORK_END = LocalTime.of(17, 0);
    private final UserRepository users;
    private final US05AvailabilityRepository meetings;
    public US05AvailabilityService(UserRepository users, US05AvailabilityRepository meetings) { this.users = users; this.meetings = meetings; }
    public List<ParticipantResponse> participants() {
        return users.findAll().stream().filter(u -> "ACTIVE".equalsIgnoreCase(u.status))
            .map(u -> new ParticipantResponse(u.userId, u.fullName, u.role == null ? null : u.role.getRoleName())).toList();
    }
    public AvailabilityResponse find(AvailabilityRequest request) {
        if (request == null || request.date() == null || request.participantIds() == null || request.participantIds().isEmpty())
            throw new IllegalArgumentException("Date and participants are required");
        int duration = request.durationMinutes() == null ? 60 : request.durationMinutes();
        if (duration < 15 || duration > 480) throw new IllegalArgumentException("Duration must be 15 to 480 minutes");
        List<String> ids = request.participantIds().stream().map(String::trim).distinct().toList();
        var selected = users.findAllById(ids);
        if (selected.size() != ids.size() || selected.stream().anyMatch(u -> !"ACTIVE".equalsIgnoreCase(u.status)))
            throw new IllegalArgumentException("Participant does not exist or is inactive");
        LocalDateTime start = request.date().atTime(WORK_START), end = request.date().atTime(WORK_END);
        List<int[]> busy = meetings.busy(ids, start, end).stream().map(m -> {
            LocalDateTime clippedStart = m.startTime.isBefore(start) ? start : m.startTime;
            LocalDateTime clippedEnd = m.endTime.isAfter(end) ? end : m.endTime;
            return new int[]{480 + (int)Duration.between(start, clippedStart).toMinutes(),
                480 + (int)Duration.between(start, clippedEnd).toMinutes()};
        }).filter(s -> s[0] < s[1]).sorted(Comparator.comparingInt(s -> s[0])).toList();
        List<int[]> merged = new ArrayList<>();
        for (int[] slot : busy) {
            if (!merged.isEmpty() && slot[0] <= merged.getLast()[1]) merged.getLast()[1] = Math.max(merged.getLast()[1], slot[1]);
            else merged.add(slot.clone());
        }
        List<SlotResponse> slots = new ArrayList<>(); int cursor = 480;
        for (int[] blocked : merged) { addFree(slots, cursor, blocked[0], duration); cursor = Math.max(cursor, blocked[1]); }
        addFree(slots, cursor, 1020, duration);
        return new AvailabilityResponse(request.date(), slots, slots.size());
    }
    private void addFree(List<SlotResponse> slots, int cursor, int until, int duration) {
        while (cursor + duration <= until) { slots.add(new SlotResponse(format(cursor), format(cursor + duration), duration)); cursor += duration; }
    }
    private String format(int value) { return "%02d:%02d".formatted(value / 60, value % 60); }
}
