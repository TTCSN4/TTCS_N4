package com.ttcs.meetingmanagement.recurrence;

import com.ttcs.meetingmanagement.dto.*;
import com.ttcs.meetingmanagement.model.*;
import com.ttcs.meetingmanagement.repository.*;
import com.ttcs.meetingmanagement.room.Room;
import jakarta.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;

@Service
public class RecurringMeetingService {
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private final EntityManager em;
    private final MeetingRepository meetings;
    private final UserRepository users;
    private final MeetingParticipantRepository participants;
    public RecurringMeetingService(EntityManager em, MeetingRepository meetings,
            UserRepository users, MeetingParticipantRepository participants) {
        this.em = em; this.meetings = meetings; this.users = users; this.participants = participants;
    }
    @Transactional
    public RecurringMeetingResponse create(RecurringMeetingRequest r) {
        List<RecurringScheduleCalculator.Occurrence> slots;
        try { slots = new RecurringScheduleCalculator().generate(r); }
        catch (IllegalArgumentException ex) { throw error(HttpStatus.BAD_REQUEST, ex.getMessage()); }
        if (!slots.getFirst().startTime().isAfter(OffsetDateTime.now()))
            throw error(HttpStatus.BAD_REQUEST, "Start must be in the future.");
        if (r.organizerId() == null || !users.existsById(r.organizerId()))
            throw error(HttpStatus.BAD_REQUEST, "Organizer does not exist.");
        Room room = em.find(Room.class, r.roomId().trim(), LockModeType.PESSIMISTIC_WRITE);
        if (room == null) throw error(HttpStatus.NOT_FOUND, "Room not found.");
        List<String> emails = r.participants() == null ? List.of() : r.participants();
        List<String> userIds = emails.stream().map(email -> users.findByEmailIgnoreCase(email.trim())
            .orElseThrow(() -> error(HttpStatus.BAD_REQUEST, "Participant does not exist: " + email)).getUserId()).toList();
        if (userIds.size() > room.getCapacity() || new HashSet<>(userIds).size() != userIds.size())
            throw error(HttpStatus.BAD_REQUEST, "Invalid participant count.");
        for (var slot : slots) {
            LocalDateTime start = local(slot.startTime()), end = local(slot.endTime());
            if (meetings.countRoomConflicts(room.getRoomId(), null, start, end) > 0)
                throw error(HttpStatus.CONFLICT, "Room conflict at " + start);
            for (Meeting m : meetings.findAll()) {
                if ("CANCELLED".equals(m.status) || !m.startTime.isBefore(end) || !m.endTime.isAfter(start)) continue;
                if (userIds.contains(m.organizerId) || participants.findByMeetingId(m.meetingId).stream()
                        .filter(p -> !Set.of("DECLINED", "REMOVED").contains(p.status)).anyMatch(p -> userIds.contains(p.userId)))
                    throw error(HttpStatus.CONFLICT, "Participant conflict at " + start);
            }
        }
        String seriesId = UUID.randomUUID().toString();
        String rule = "frequency=" + r.frequency() + ";interval=" + r.interval() + ";weekdays=" + r.weekdays()
            + ";monthDay=" + r.monthDay() + ";until=" + r.until() + ";count=" + r.occurrences();
        List<String> ids = new ArrayList<>();
        for (var slot : slots) {
            Meeting m = new Meeting();
            m.meetingId = UUID.randomUUID().toString(); m.organizerId = r.organizerId();
            m.title = r.title().trim(); m.description = r.description(); m.roomId = room.getRoomId();
            m.startTime = local(slot.startTime()); m.endTime = local(slot.endTime()); m.status = "SCHEDULED";
            m.isRecurring = true; m.recurrenceRule = rule; m.recurrenceSeriesId = seriesId;
            meetings.save(m); ids.add(m.meetingId);
            for (String userId : userIds) {
                MeetingParticipant p = new MeetingParticipant(); p.meetingId = m.meetingId; p.userId = userId;
                p.status = "INVITED"; participants.save(p);
            }
        }
        MeetingRecurrence config = new MeetingRecurrence();
        config.id = seriesId; config.configuration = rule; config.occurrences = ids.size(); em.persist(config);
        return new RecurringMeetingResponse(seriesId, rule, ids.size(), ids);
    }
    private static LocalDateTime local(OffsetDateTime value) { return value.atZoneSameInstant(VN).toLocalDateTime(); }
    private static ResponseStatusException error(HttpStatus status, String message) { return new ResponseStatusException(status, message); }
}
