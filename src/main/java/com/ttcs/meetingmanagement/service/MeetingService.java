package com.ttcs.meetingmanagement.service;

import com.ttcs.meetingmanagement.dto.CreateMeetingRequest;
import com.ttcs.meetingmanagement.dto.MeetingResponse;
import com.ttcs.meetingmanagement.exception.MeetingException;
import com.ttcs.meetingmanagement.model.Meeting;
import com.ttcs.meetingmanagement.repository.MeetingRepository;
import com.ttcs.meetingmanagement.room.Room;
import com.ttcs.meetingmanagement.room.RoomRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class MeetingService {

    private final MeetingRepository meetingRepository;
    private final RoomRepository roomRepository;

    @Autowired
    public MeetingService(
            MeetingRepository meetingRepository,
            @Autowired(required = false) RoomRepository roomRepository) {
        this.meetingRepository = meetingRepository;
        this.roomRepository = roomRepository;
    }

    public MeetingService(MeetingRepository meetingRepository) {
        this(meetingRepository, null);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public MeetingResponse createMeeting(CreateMeetingRequest request) {

        // 1. Kiem tra thoi gian
        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new MeetingException(
                    "Thời gian kết thúc phải sau thời gian bắt đầu",
                    HttpStatus.BAD_REQUEST
            );
        }

        // 2. Kiem tra organizer co ton tai trong bang user
        if (meetingRepository.countUserById(
                request.getOrganizerId()) == 0) {

            throw new MeetingException(
                    "Organizer không tồn tại",
                    HttpStatus.NOT_FOUND
            );
        }

        // 3. Neu co roomId thi kiem tra phong va khoa phong
        if (request.getRoomId() != null
                && !request.getRoomId().isBlank()) {

            String roomId = request.getRoomId().trim();

            if (roomRepository != null) {
                Room room = roomRepository.findLockedById(roomId)
                        .orElseThrow(() -> new MeetingException(
                                "Phòng họp không tồn tại",
                                HttpStatus.NOT_FOUND
                        ));

                if (room.getStatus() == null || !"AVAILABLE".equalsIgnoreCase(room.getStatus().trim())) {
                    throw new MeetingException(
                            "Phòng họp không khả dụng",
                            HttpStatus.BAD_REQUEST
                    );
                }
            } else {
                if (meetingRepository.countRoomById(roomId) == 0) {
                    throw new MeetingException(
                            "Phòng họp không tồn tại",
                            HttpStatus.NOT_FOUND
                    );
                }
            }

            // 4. Kiem tra phong co bi trung lich sau khi da khoa
            long conflictCount =
                    meetingRepository.countRoomConflicts(
                            roomId,
                            null,
                            request.getStartTime(),
                            request.getEndTime()
                    );

            if (conflictCount > 0) {
                throw new MeetingException(
                        "Phòng họp đã được đặt trong khoảng thời gian này",
                        HttpStatus.CONFLICT
                );
            }
        }

        // 5. Kiem tra lich lap
        boolean recurring =
                Boolean.TRUE.equals(
                        request.getIsRecurring()
                );

        if (recurring
                && (request.getRecurrenceRule() == null
                || request.getRecurrenceRule().isBlank())) {

            throw new MeetingException(
                    "Recurrence rule không được để trống khi tạo lịch lặp",
                    HttpStatus.BAD_REQUEST
            );
        }

        // 6. Tao doi tuong Meeting
        Meeting meeting = new Meeting();

        meeting.setMeetingId(
                UUID.randomUUID().toString()
        );

        meeting.setOrganizerId(
                request.getOrganizerId()
        );

        meeting.setRoomId(
                request.getRoomId() != null && !request.getRoomId().isBlank() ? request.getRoomId().trim() : null
        );

        meeting.setTitle(
                request.getTitle().trim()
        );

        meeting.setDescription(
                request.getDescription()
        );

        meeting.setStartTime(
                request.getStartTime()
        );

        meeting.setEndTime(
                request.getEndTime()
        );

        // Meeting moi mac dinh la SCHEDULED
        meeting.setStatus("SCHEDULED");

        meeting.setIsRecurring(recurring);

        // 7. Xu ly lich lap
        if (recurring) {

            meeting.setRecurrenceRule(
                    request.getRecurrenceRule()
            );

            meeting.setRecurrenceSeriesId(
                    UUID.randomUUID().toString()
            );

        } else {

            meeting.setRecurrenceRule(null);
            meeting.setRecurrenceSeriesId(null);
        }

        // Meeting moi chua bi huy
        meeting.setCancelReason(null);
        meeting.setCancelledAt(null);

        // 8. Thoi gian tao/cap nhat
        LocalDateTime now =
                LocalDateTime.now();

        meeting.setCreatedAt(now);
        meeting.setUpdatedAt(now);

        // 9. Luu xuong database
        Meeting saved =
                meetingRepository.saveAndFlush(meeting);

        // 10. Tra response
        return toResponse(saved);
    }

    private MeetingResponse toResponse(Meeting meeting) {

        MeetingResponse response =
                new MeetingResponse();

        response.setMeetingId(
                meeting.getMeetingId()
        );

        response.setOrganizerId(
                meeting.getOrganizerId()
        );

        response.setRoomId(
                meeting.getRoomId()
        );

        response.setTitle(
                meeting.getTitle()
        );

        response.setDescription(
                meeting.getDescription()
        );

        response.setStartTime(
                meeting.getStartTime()
        );

        response.setEndTime(
                meeting.getEndTime()
        );

        response.setStatus(
                meeting.getStatus()
        );

        response.setIsRecurring(
                meeting.getIsRecurring()
        );

        response.setRecurrenceRule(
                meeting.getRecurrenceRule()
        );

        response.setRecurrenceSeriesId(
                meeting.getRecurrenceSeriesId()
        );

        response.setCreatedAt(
                meeting.getCreatedAt()
        );

        response.setUpdatedAt(
                meeting.getUpdatedAt()
        );

        return response;
    }
}