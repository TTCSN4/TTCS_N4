package com.ttcs.meetingmanagement.meeting.availability;
import com.ttcs.meetingmanagement.meeting.*;
import com.ttcs.meetingmanagement.meeting.participant.*;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;
import static com.ttcs.meetingmanagement.meeting.availability.AvailabilityDtos.*;
@Service
public class AvailabilityService {
    private static final LocalTime WORK_START = LocalTime.of(8, 0), WORK_END = LocalTime.of(17, 0);
    private final ParticipantRepository participantRepository; private final MeetingRepository meetingRepository;
    public AvailabilityService(ParticipantRepository p, MeetingRepository m) { participantRepository = p; meetingRepository = m; }
    public List<ParticipantResponse> participants() { return participantRepository.findAll().stream().map(p -> new ParticipantResponse(p.getId(), p.getName(), p.getRole())).toList(); }
    public AvailabilityResponse find(AvailabilityRequest request) {
        if (request == null || request.date() == null) throw new IllegalArgumentException("Ngày họp không hợp lệ.");
        if (request.participantIds() == null || request.participantIds().isEmpty()) throw new IllegalArgumentException("Vui lòng chọn ít nhất một người tham dự.");
        int duration = request.durationMinutes() == null ? 60 : request.durationMinutes();
        if (duration < 15 || duration > 480) throw new IllegalArgumentException("Thời lượng phải từ 15 đến 480 phút.");
        List<Participant> selected = participantRepository.findAllById(request.participantIds());
        if (selected.size() != new HashSet<>(request.participantIds()).size()) throw new IllegalArgumentException("Người tham dự không hợp lệ.");
        LocalDateTime start = request.date().atTime(WORK_START), end = request.date().atTime(WORK_END);
        List<Meeting> meetings = meetingRepository.findDistinctByParticipants_IdInAndStartTimeLessThanAndEndTimeGreaterThan(request.participantIds(), end, start);
        List<int[]> busy = meetings.stream().map(m -> new int[]{Math.max(minutes(m.getStartTime().toLocalTime()), minutes(WORK_START)), Math.min(minutes(m.getEndTime().toLocalTime()), minutes(WORK_END))}).filter(s -> s[0] < s[1]).sorted(Comparator.comparingInt(s -> s[0])).toList();
        List<int[]> merged = new ArrayList<>();
        for (int[] slot : busy) { if (!merged.isEmpty() && slot[0] <= merged.getLast()[1]) merged.getLast()[1] = Math.max(merged.getLast()[1], slot[1]); else merged.add(new int[]{slot[0], slot[1]}); }
        List<SlotResponse> slots = new ArrayList<>(); int cursor = minutes(WORK_START);
        for (int[] blocked : merged) { cursor = addFree(slots, cursor, blocked[0], duration); cursor = Math.max(cursor, blocked[1]); }
        addFree(slots, cursor, minutes(WORK_END), duration);
        return new AvailabilityResponse(request.date(), slots, slots.size());
    }
    private int addFree(List<SlotResponse> slots, int cursor, int until, int duration) { while (cursor + duration <= until) { slots.add(new SlotResponse(format(cursor), format(cursor + duration), duration)); cursor += duration; } return cursor; }
    private int minutes(LocalTime time) { return time.getHour() * 60 + time.getMinute(); }
    private String format(int value) { return "%02d:%02d".formatted(value / 60, value % 60); }
}
