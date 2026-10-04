package com.ttcs.meetingmanagement.roombooking;

import com.ttcs.meetingmanagement.dto.RoomBookingRequest;
import com.ttcs.meetingmanagement.dto.RoomBookingResponse;
import com.ttcs.meetingmanagement.model.Meeting;
import com.ttcs.meetingmanagement.model.MeetingStatus;
import com.ttcs.meetingmanagement.repository.MeetingRepository;
import com.ttcs.meetingmanagement.room.Room;
import com.ttcs.meetingmanagement.room.RoomRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RoomBookingService {

    private final MeetingRepository meetingRepository;
    private final RoomRepository roomRepository;

    public RoomBookingService(
            MeetingRepository meetingRepository,
            RoomRepository roomRepository) {

        this.meetingRepository = meetingRepository;
        this.roomRepository = roomRepository;
    }

    @Transactional
    public RoomBookingResponse bookRoom(RoomBookingRequest request) {

        // 1. Kiểm tra cuộc họp tồn tại
        Meeting meeting = meetingRepository
                .findById(request.getMeetingId())
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Meeting not found: " + request.getMeetingId()
                        )
                );

        // 2. Không cho đặt phòng cho cuộc họp đã hủy
        if (meeting.getStatus() == MeetingStatus.CANCELLED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cannot book room for a cancelled meeting"
            );
        }

        // 3. Chuẩn hóa mã phòng
        String roomId = request.getRoomId().trim();

        // 4. Kiểm tra phòng tồn tại
        Room room = roomRepository
                .findById(roomId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Room not found: " + roomId
                        )
                );

        // 5. Kiểm tra thời gian Meeting hợp lệ
        if (meeting.getStartTime() == null
                || meeting.getEndTime() == null
                || !meeting.getEndTime().isAfter(meeting.getStartTime())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Meeting time is invalid"
            );
        }

        // 6. Kiểm tra trùng lịch phòng
        long conflictCount =
                meetingRepository.countRoomConflicts(
                        roomId,
                        meeting.getId(),
                        MeetingStatus.CANCELLED,
                        meeting.getStartTime(),
                        meeting.getEndTime()
                );

        if (conflictCount > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Room is already booked in this time range"
            );
        }

        // 7. Gán phòng cho Meeting
        meeting.setRoom(room.getRoomId());

        // 8. Lưu xuống database
        Meeting savedMeeting =
                meetingRepository.save(meeting);

        // 9. Trả kết quả
        return new RoomBookingResponse(
                savedMeeting.getId(),
                savedMeeting.getRoom(),
                savedMeeting.getStartTime(),
                savedMeeting.getEndTime(),
                "Room booked successfully"
        );
    }
}