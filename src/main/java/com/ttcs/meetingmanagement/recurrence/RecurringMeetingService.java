<<<<<<< HEAD

package com.ttcs.meetingmanagement.recurrence;

import com.ttcs.meetingmanagement.dto.RecurringMeetingRequest;
import com.ttcs.meetingmanagement.dto.RecurringMeetingResponse;

import jakarta.persistence.EntityManager;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class RecurringMeetingService {

    private static final ZoneId DB_ZONE =
            ZoneId.of("Asia/Ho_Chi_Minh");

    private final EntityManager entityManager;
    private final RecurringScheduleCalculator calculator =
            new RecurringScheduleCalculator();

    public RecurringMeetingService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional
    public RecurringMeetingResponse create(
            RecurringMeetingRequest request) {

        // 1. Kiem tra request
        try {
            RecurringMeetingRules.validate(request);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, ex.getMessage());
        }

        String roomId = request.roomId().trim();
        String organizerId = request.organizerId().trim();

        // 2. Khoa phong trong transaction
        List<?> rooms = entityManager.createNativeQuery(
                "SELECT room_id FROM room " +
                "WHERE room_id = :roomId FOR UPDATE"
        )
        .setParameter("roomId", roomId)
        .getResultList();

        if (rooms.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Phong hop khong ton tai");
        }

        // 3. Kiem tra nguoi to chuc
        Number users = (Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM `user` " +
                "WHERE user_id = :userId"
        )
        .setParameter("userId", organizerId)
        .getSingleResult();

        if (users.longValue() == 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Nguoi to chuc khong ton tai");
        }

        // 4. Sinh danh sach lich hop
        var schedules = calculator.generate(
                request.startTime(),
                request.endTime(),
                request.frequency(),
                request.interval(),
                request.occurrences()
        );

        // 5. Kiem tra toan bo lich truoc khi luu
        for (int i = 0; i < schedules.size(); i++) {

            var schedule = schedules.get(i);

            if (i > 0 && schedules.get(i - 1)
                    .endTime().isAfter(schedule.startTime())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Cac lan hop trong chuoi bi trung nhau");
            }

            Number conflicts =
                    (Number) entityManager.createNativeQuery("""
                        SELECT COUNT(*)
                        FROM meetings
                        WHERE room_id = :roomId
                          AND status <> 'CANCELLED'
                          AND start_time < :endTime
                          AND end_time > :startTime
                    """)
                    .setParameter("roomId", roomId)
                    .setParameter("startTime",
                            toDatabaseTime(schedule.startTime()))
                    .setParameter("endTime",
                            toDatabaseTime(schedule.endTime()))
                    .getSingleResult();

            if (conflicts.longValue() > 0) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Trung lich phong tai lan hop " + (i + 1));
            }
        }

        // 6. Sinh ma va quy tac lap
        String seriesId = UUID.randomUUID().toString();
        String rule = RecurringMeetingRules.buildRule(request);
        LocalDateTime now = LocalDateTime.now(DB_ZONE);

        List<String> meetingIds = new ArrayList<>();

        // 7. Luu tat ca cuoc hop
        for (var schedule : schedules) {

            String meetingId = UUID.randomUUID().toString();

            entityManager.createNativeQuery("""
                INSERT INTO meetings (
                    meeting_id,
                    organizer_id,
                    room_id,
                    title,
                    description,
                    start_time,
                    end_time,
                    status,
                    is_recurring,
                    recurrence_rule,
                    recurrence_series_id,
                    created_at,
                    updated_at
                ) VALUES (
                    :meetingId,
                    :organizerId,
                    :roomId,
                    :title,
                    :description,
                    :startTime,
                    :endTime,
                    :status,
                    :isRecurring,
                    :rule,
                    :seriesId,
                    :createdAt,
                    :updatedAt
                )
            """)
            .setParameter("meetingId", meetingId)
            .setParameter("organizerId", organizerId)
            .setParameter("roomId", roomId)
            .setParameter("title", request.title().trim())
            .setParameter("description", request.description())
            .setParameter("startTime",
                    toDatabaseTime(schedule.startTime()))
            .setParameter("endTime",
                    toDatabaseTime(schedule.endTime()))
            .setParameter("status", "SCHEDULED")
            .setParameter("isRecurring", true)
            .setParameter("rule", rule)
            .setParameter("seriesId", seriesId)
            .setParameter("createdAt", now)
            .setParameter("updatedAt", now)
            .executeUpdate();

            meetingIds.add(meetingId);
        }

        // 8. Tra ve ket qua
        return new RecurringMeetingResponse(
                seriesId,
                rule,
                meetingIds.size(),
                meetingIds
        );
    }

    private LocalDateTime toDatabaseTime(OffsetDateTime time) {
        return time.atZoneSameInstant(DB_ZONE)
                .toLocalDateTime();
    }
=======
package com.ttcs.meetingmanagement.recurrence;
import com.ttcs.meetingmanagement.dto.*;
import com.ttcs.meetingmanagement.model.*;
import com.ttcs.meetingmanagement.repository.MeetingRepository;
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
 private final EntityManager em;private final MeetingRepository meetings;
 public RecurringMeetingService(EntityManager em,MeetingRepository meetings){this.em=em;this.meetings=meetings;}
 @Transactional public RecurringMeetingResponse create(RecurringMeetingRequest r){
  List<RecurringScheduleCalculator.Occurrence> slots;
  try{slots=new RecurringScheduleCalculator().generate(r);}catch(IllegalArgumentException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,e.getMessage());}
  if(!slots.getFirst().startTime().isAfter(OffsetDateTime.now()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Start must be in the future.");
  Room room=em.find(Room.class,r.roomId().trim(),LockModeType.PESSIMISTIC_WRITE);
  if(room==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Room not found.");
  List<String> participants=r.participants()==null?List.of():r.participants().stream().map(String::trim).toList();
  if(participants.size()>room.getCapacity())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Participants exceed room capacity.");
  if(participants.stream().map(String::toLowerCase).distinct().count()!=participants.size())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Duplicate participant emails.");
  var existing=meetings.findAllByOrderByStartTimeAsc();
  for(var slot:slots){
   if(meetings.countRoomConflicts(room.getRoomId(),-1L,MeetingStatus.CANCELLED,slot.startTime(),slot.endTime())>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Room conflict at "+slot.startTime());
   boolean conflict=existing.stream().filter(m->m.getStatus()!=MeetingStatus.CANCELLED).filter(m->m.getStartTime().isBefore(slot.endTime())&&m.getEndTime().isAfter(slot.startTime())).anyMatch(m->m.getParticipants().stream().anyMatch(participants::contains));
   if(conflict)throw new ResponseStatusException(HttpStatus.CONFLICT,"Participant conflict at "+slot.startTime());
  }
  Long seriesId=null;List<Long> ids=new ArrayList<>();
  for(var slot:slots){Meeting m=new Meeting();m.setTitle(r.title().trim());m.setDescription(r.description());m.setRoom(room.getRoomId());m.setStartTime(slot.startTime());m.setEndTime(slot.endTime());m.setOrganizerId(r.organizerId()==null?1L:r.organizerId());m.setParticipants(new ArrayList<>(participants));m.setStatus(MeetingStatus.SCHEDULED);m.setCreatedAt(OffsetDateTime.now());m.setUpdatedAt(OffsetDateTime.now());meetings.save(m);if(seriesId==null)seriesId=m.getId();m.setRecurrenceSeriesId(seriesId);ids.add(m.getId());}
  String rule="frequency="+r.frequency()+";interval="+(r.interval()==null?1:r.interval())+";weekdays="+r.weekdays()+";monthDay="+r.monthDay()+";until="+r.until()+";count="+r.occurrences();
  MeetingRecurrence config=new MeetingRecurrence();config.id=seriesId;config.configuration=rule;config.occurrences=ids.size();em.persist(config);
  return new RecurringMeetingResponse(seriesId,rule,ids.size(),ids);
 }
>>>>>>> parent of 64bacae (chore(US03): remove backend files and preserve directories)
}
