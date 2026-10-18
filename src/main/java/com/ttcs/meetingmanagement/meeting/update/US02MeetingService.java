package com.ttcs.meetingmanagement.meeting.update;

import com.ttcs.meetingmanagement.model.Meeting;
import com.ttcs.meetingmanagement.model.EquipmentBookingStatus;
import com.ttcs.meetingmanagement.repository.*;
import com.ttcs.meetingmanagement.room.RoomRepository;
import com.ttcs.meetingmanagement.service.RoomBookingPermissionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;

@Service
public class US02MeetingService {
    private final MeetingRepository meetings;
    private final RoomRepository rooms;
    private final EquipmentBookingRepository equipment;
    private final RoomBookingPermissionService permissions;
    public US02MeetingService(MeetingRepository meetings, RoomRepository rooms,
            EquipmentBookingRepository equipment, RoomBookingPermissionService permissions) {
        this.meetings = meetings; this.rooms = rooms; this.equipment = equipment; this.permissions = permissions;
    }

    @Transactional(readOnly = true)
    public Meeting get(String id) { return meetings.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Meeting not found")); }

    @Transactional
    public Meeting update(String id, String actor, US02MeetingRequest request) {
        Meeting m = locked(id, actor);
        if (request.startTime() == null || request.endTime() == null
                || !request.endTime().isAfter(request.startTime()) || !request.startTime().isAfter(LocalDateTime.now()))
            throw error(HttpStatus.BAD_REQUEST, "Meeting must have a valid future time range");
        String room = request.roomId() == null ? m.roomId : request.roomId().trim();
        if (room != null && !room.isBlank()) {
            rooms.findLockedById(room).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Room not found"));
            permissions.assertCanBook(actor, room);
            if (meetings.countRoomConflicts(room, id, request.startTime(), request.endTime()) > 0)
                throw error(HttpStatus.CONFLICT, "Room is already booked");
        }
        // Existing reservations use the old room/time. Release them before rebooking.
        if (!java.util.Objects.equals(room, m.roomId) || !request.startTime().equals(m.startTime) || !request.endTime().equals(m.endTime))
            releaseEquipment(id);
        m.title = request.title().trim(); m.description = request.description();
        m.startTime = request.startTime(); m.endTime = request.endTime();
        m.roomId = room == null || room.isBlank() ? null : room;
        return meetings.saveAndFlush(m);
    }

    @Transactional
    public void cancel(String id, String actor, String reason) {
        Meeting m = locked(id, actor);
        m.status = "CANCELLED"; m.cancelReason = reason; m.cancelledAt = LocalDateTime.now();
        releaseEquipment(id); meetings.saveAndFlush(m);
    }

    private Meeting locked(String id, String actor) {
        Meeting m = meetings.findByIdForUpdate(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Meeting not found"));
        if (!m.organizerId.equals(actor)) throw error(HttpStatus.FORBIDDEN, "Only the organizer can modify this meeting");
        if (!"SCHEDULED".equals(m.status) || !m.startTime.isAfter(LocalDateTime.now()))
            throw error(HttpStatus.CONFLICT, "Only a future scheduled meeting can be modified");
        return m;
    }
    private void releaseEquipment(String id) {
        var bookings = equipment.findAllByMeetingId(id);
        bookings.forEach(b -> b.setStatus(EquipmentBookingStatus.CANCELLED));
        equipment.saveAll(bookings);
    }
    private static ResponseStatusException error(HttpStatus status, String message) { return new ResponseStatusException(status, message); }
}
