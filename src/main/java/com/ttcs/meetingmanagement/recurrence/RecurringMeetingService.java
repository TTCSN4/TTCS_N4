
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
}
