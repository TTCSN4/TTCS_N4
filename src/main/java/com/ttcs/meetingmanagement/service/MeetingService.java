package com.ttcs.meetingmanagement.service;

import com.ttcs.meetingmanagement.dto.CreateMeetingRequest;
import com.ttcs.meetingmanagement.dto.MeetingResponse;
import com.ttcs.meetingmanagement.entity.Meeting;
import com.ttcs.meetingmanagement.exception.MeetingException;
import com.ttcs.meetingmanagement.repository.MeetingRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class MeetingService {

    private final MeetingRepository meetingRepository;

    public MeetingService(MeetingRepository meetingRepository) {
        this.meetingRepository = meetingRepository;
    }

    public MeetingResponse createMeeting(CreateMeetingRequest request) {

        // 1. Kiểm tra thời gian
        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new MeetingException(
                    "Thời gian kết thúc phải sau thời gian bắt đầu",
                    HttpStatus.BAD_REQUEST
            );
        }

        // 2. Kiểm tra organizer có tồn tại trong bảng user
        if (meetingRepository.countUserById(
                request.getOrganizerId()) == 0) {

            throw new MeetingException(
                    "Organizer không tồn tại",
                    HttpStatus.NOT_FOUND
            );
        }

        // 3. Nếu có roomId thì kiểm tra phòng
        if (request.getRoomId() != null
                && !request.getRoomId().isBlank()) {

            // Kiểm tra room tồn tại
            if (meetingRepository.countRoomById(
                    request.getRoomId()) == 0) {

                throw new MeetingException(
                        "Phòng họp không tồn tại",
                        HttpStatus.NOT_FOUND
                );
            }

            // 4. Kiểm tra phòng có bị trùng lịch
            long conflictCount =
                    meetingRepository.countRoomConflicts(
                            request.getRoomId(),
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

        // 5. Kiểm tra lịch lặp
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

        // 6. Tạo đối tượng Meeting
        Meeting meeting = new Meeting();

        meeting.setMeetingId(
                UUID.randomUUID().toString()
        );

        meeting.setOrganizerId(
                request.getOrganizerId()
        );

        meeting.setRoomId(
                request.getRoomId()
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

        // Meeting mới mặc định là SCHEDULED
        meeting.setStatus("SCHEDULED");

        meeting.setIsRecurring(recurring);

        // 7. Xử lý lịch lặp
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

        // Meeting mới chưa bị hủy
        meeting.setCancelReason(null);
        meeting.setCancelledAt(null);

        // 8. Thời gian tạo/cập nhật
        LocalDateTime now =
                LocalDateTime.now();

        meeting.setCreatedAt(now);
        meeting.setUpdatedAt(now);

        // 9. Lưu xuống database
        Meeting saved =
                meetingRepository.save(meeting);

        // 10. Trả response
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