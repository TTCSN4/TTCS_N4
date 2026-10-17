package com.ttcs.meetingmanagement.roombooking;

import com.ttcs.meetingmanagement.dto.RoomBookingRequest;
import com.ttcs.meetingmanagement.dto.RoomBookingResponse;
import com.ttcs.meetingmanagement.model.Meeting;
import com.ttcs.meetingmanagement.rbac.RbacAccess;
import com.ttcs.meetingmanagement.repository.MeetingRepository;
import com.ttcs.meetingmanagement.room.RoomRepository;
import com.ttcs.meetingmanagement.security.BackendActor;
import com.ttcs.meetingmanagement.service.RoomBookingPermissionService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Service
public class RoomBookingService {

    private final MeetingRepository meetingRepository;
    private final RoomRepository roomRepository;
    private final RoomBookingPermissionService permissionService;
    private final RbacAccess rbacAccess;

    @Autowired
    public RoomBookingService(
            MeetingRepository meetingRepository,
            RoomRepository roomRepository,
            RoomBookingPermissionService permissionService,
            @Autowired(required = false) RbacAccess rbacAccess) {

        this.meetingRepository = meetingRepository;
        this.roomRepository = roomRepository;
        this.permissionService = permissionService;
        this.rbacAccess = rbacAccess;
    }

    public RoomBookingService(
            MeetingRepository meetingRepository,
            RoomRepository roomRepository,
            RoomBookingPermissionService permissionService) {
        this(meetingRepository, roomRepository, permissionService, null);
    }

    // US08 + US21: Dat phong va kiem tra quyen
    @Transactional
    public RoomBookingResponse bookRoom(
            RoomBookingRequest request) {

        if (request == null
                || request.getMeetingId() == null
                || request.getMeetingId().isBlank()
                || request.getRoomId() == null
                || request.getRoomId().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Meeting ID and Room ID are required"
            );
        }

        String meetingId = request.getMeetingId().trim();
        String roomId = request.getRoomId().trim();

        // 1. Kiem tra Meeting
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Meeting not found"
                ));

        // Kiem tra nguoi thao tac (Admin hoac Organizer)
        String actorId = BackendActor.requireUserId();
        boolean isAdmin = rbacAccess != null && rbacAccess.isAdmin(actorId);
        if (!actorId.equals(meeting.getOrganizerId()) && !isAdmin) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only the organizer or an administrator can book a room for this meeting"
            );
        }

        // 2. Kiem tra trang thai
        if ("CANCELLED".equalsIgnoreCase(meeting.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cannot book a cancelled meeting"
            );
        }

        // 3. Kiem tra phong
        if (!roomRepository.existsById(roomId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Room not found"
            );
        }

        // 4. Kiem tra thoi gian
        if (meeting.getStartTime() == null
                || meeting.getEndTime() == null
                || !meeting.getEndTime().isAfter(
                        meeting.getStartTime())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid meeting time"
            );
        }

        // 5. US21: Kiem tra quyen theo USER, ROLE, DEPARTMENT
        String userId = meeting.getOrganizerId();

        permissionService.assertCanBook(userId, roomId);

        // 6. US08: Kiem tra trung lich
        long conflicts = meetingRepository.countRoomConflicts(
                roomId,
                meetingId,
                meeting.getStartTime(),
                meeting.getEndTime()
        );

        if (conflicts > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Room already booked"
            );
        }

        // 7. Luu thong tin dat phong
        meeting.setRoomId(roomId);
        meeting.setUpdatedAt(LocalDateTime.now());

        Meeting saved = meetingRepository.save(meeting);

        return new RoomBookingResponse(
                saved.getMeetingId(),
                saved.getRoomId(),
                saved.getStartTime(),
                saved.getEndTime(),
                "Room booked successfully"
        );
    }

    // US09: Huy dat phong
    @Transactional
    public RoomBookingResponse cancelRoomBooking(String meetingId) {

        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Meeting not found"
                ));

        String actorId = BackendActor.requireUserId();
        boolean isAdmin = rbacAccess != null && rbacAccess.isAdmin(actorId);
        if (!actorId.equals(meeting.getOrganizerId()) && !isAdmin) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only the organizer or an administrator can cancel room booking for this meeting"
            );
        }

        if (meeting.getRoomId() == null
                || meeting.getRoomId().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Meeting has no room booking"
            );
        }

        String oldRoomId = meeting.getRoomId();

        LocalDateTime startTime = meeting.getStartTime();
        LocalDateTime endTime = meeting.getEndTime();

        meeting.setRoomId(null);
        meeting.setUpdatedAt(LocalDateTime.now());

        meetingRepository.save(meeting);

        return new RoomBookingResponse(
                meeting.getMeetingId(),
                oldRoomId,
                startTime,
                endTime,
                "Room booking cancelled successfully"
        );
    }
}
