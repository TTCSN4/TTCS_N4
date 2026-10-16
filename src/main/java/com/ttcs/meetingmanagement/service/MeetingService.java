package com.ttcs.meetingmanagement.service;

import com.ttcs.meetingmanagement.dto.CreateMeetingRequest;
import com.ttcs.meetingmanagement.dto.MeetingResponse;
import com.ttcs.meetingmanagement.model.Meeting;
import com.ttcs.meetingmanagement.model.MeetingStatus;
import com.ttcs.meetingmanagement.exception.MeetingException;
import com.ttcs.meetingmanagement.repository.MeetingRepository;
import com.ttcs.meetingmanagement.room.RoomRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Service
public class MeetingService {

    private final MeetingRepository meetingRepository;
    private final RoomRepository roomRepository;

    public MeetingService(MeetingRepository meetingRepository, RoomRepository roomRepository) {
        this.meetingRepository = meetingRepository;
        this.roomRepository = roomRepository;
    }

    public MeetingResponse createMeeting(CreateMeetingRequest request) {

        // 1. Kiểm tra tiêu đề
        if (request.getTitle() == null || request.getTitle().isBlank()) {
            throw new MeetingException(
                    "Tiêu đề cuộc họp không được để trống",
                    HttpStatus.BAD_REQUEST
            );
        }

        // 2. Kiểm tra thời gian
        if (request.getStartTime() == null || request.getEndTime() == null) {
            throw new MeetingException(
                    "Thời gian bắt đầu và kết thúc không được để trống",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new MeetingException(
                    "Thời gian kết thúc phải sau thời gian bắt đầu",
                    HttpStatus.BAD_REQUEST
            );
        }

        // 3. Kiểm tra organizerId
        if (request.getOrganizerId() == null || request.getOrganizerId().isBlank()) {
            throw new MeetingException(
                    "Organizer không tồn tại",
                    HttpStatus.NOT_FOUND
            );
        }

        Long organizerId;
        try {
            organizerId = Long.parseLong(request.getOrganizerId().trim());
            if (organizerId <= 0) {
                throw new MeetingException(
                        "Organizer không tồn tại",
                        HttpStatus.NOT_FOUND
                );
            }
        } catch (NumberFormatException e) {
            throw new MeetingException(
                    "Organizer không tồn tại",
                    HttpStatus.NOT_FOUND
            );
        }

        OffsetDateTime startOffset = toOffsetDateTime(request.getStartTime());
        OffsetDateTime endOffset = toOffsetDateTime(request.getEndTime());
        String roomId = request.getRoomId() != null && !request.getRoomId().isBlank()
                ? request.getRoomId().trim()
                : null;

        // 4. Nếu có roomId thì kiểm tra phòng
        if (roomId != null) {

            // Kiểm tra room tồn tại
            if (!roomRepository.existsById(roomId)) {
                throw new MeetingException(
                        "Phòng họp không tồn tại",
                        HttpStatus.NOT_FOUND
                );
            }

            // 5. Kiểm tra phòng có bị trùng lịch
            long conflictCount =
                    meetingRepository.countRoomConflicts(
                            roomId,
                            -1L,
                            MeetingStatus.CANCELLED,
                            startOffset,
                            endOffset
                    );

            if (conflictCount > 0) {
                throw new MeetingException(
                        "Phòng họp đã được đặt trong khoảng thời gian này",
                        HttpStatus.CONFLICT
                );
            }
        }

        // 6. Kiểm tra lịch lặp
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

        // 7. Tạo đối tượng Meeting
        Meeting meeting = new Meeting();

        meeting.setOrganizerId(organizerId);

        if (roomId != null) {
            meeting.setRoom(roomId);
        }

        meeting.setTitle(
                request.getTitle().trim()
        );

        meeting.setDescription(
                request.getDescription()
        );

        meeting.setStartTime(
                startOffset
        );

        meeting.setEndTime(
                endOffset
        );

        // Meeting mới mặc định là SCHEDULED
        meeting.setStatus(MeetingStatus.SCHEDULED);

        // 8. Thời gian tạo/cập nhật
        OffsetDateTime now = OffsetDateTime.now();
        meeting.setCreatedAt(now);
        meeting.setUpdatedAt(now);

        // 9. Lưu xuống database
        Meeting saved = meetingRepository.save(meeting);

        // 10. Xử lý lịch lặp
        if (recurring) {
            saved.setRecurrenceSeriesId(saved.getId());
            saved = meetingRepository.save(saved);
        }

        // 11. Trả response
        return toResponse(saved, recurring, request.getRecurrenceRule());
    }

    private MeetingResponse toResponse(Meeting meeting, boolean recurring, String recurrenceRule) {

        MeetingResponse response = new MeetingResponse();

        response.setMeetingId(
                meeting.getId() != null ? String.valueOf(meeting.getId()) : null
        );

        response.setOrganizerId(
                meeting.getOrganizerId() != null ? String.valueOf(meeting.getOrganizerId()) : null
        );

        response.setRoomId(
                meeting.getRoom()
        );

        response.setTitle(
                meeting.getTitle()
        );

        response.setDescription(
                meeting.getDescription()
        );

        response.setStartTime(
                meeting.getStartTime() != null ? meeting.getStartTime().toLocalDateTime() : null
        );

        response.setEndTime(
                meeting.getEndTime() != null ? meeting.getEndTime().toLocalDateTime() : null
        );

        response.setStatus(
                meeting.getStatus() != null ? meeting.getStatus().name() : null
        );

        response.setIsRecurring(recurring);

        response.setRecurrenceRule(
                recurring ? recurrenceRule : null
        );

        response.setRecurrenceSeriesId(
                meeting.getRecurrenceSeriesId() != null
                        ? String.valueOf(meeting.getRecurrenceSeriesId())
                        : null
        );

        response.setCreatedAt(
                meeting.getCreatedAt() != null ? meeting.getCreatedAt().toLocalDateTime() : null
        );

        response.setUpdatedAt(
                meeting.getUpdatedAt() != null ? meeting.getUpdatedAt().toLocalDateTime() : null
        );

        return response;
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime ldt) {
        if (ldt == null) return null;
        return ldt.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }
}