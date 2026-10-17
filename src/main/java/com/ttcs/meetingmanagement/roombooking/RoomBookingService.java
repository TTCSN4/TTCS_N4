package com.ttcs.meetingmanagement.roombooking;

import com.ttcs.meetingmanagement.dto.RoomBookingRequest;
import com.ttcs.meetingmanagement.dto.RoomBookingResponse;
import com.ttcs.meetingmanagement.model.EquipmentBooking;
import com.ttcs.meetingmanagement.model.EquipmentBookingStatus;
import com.ttcs.meetingmanagement.model.Meeting;
import com.ttcs.meetingmanagement.repository.EquipmentBookingRepository;
import com.ttcs.meetingmanagement.repository.MeetingRepository;
import com.ttcs.meetingmanagement.room.Room;
import com.ttcs.meetingmanagement.room.RoomRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Service
public class RoomBookingService {

    private final MeetingRepository meetingRepository;
    private final RoomRepository roomRepository;
    private final EquipmentBookingRepository equipmentBookingRepository;

    public RoomBookingService(
            MeetingRepository meetingRepository,
            RoomRepository roomRepository,
            EquipmentBookingRepository equipmentBookingRepository) {

        this.meetingRepository = meetingRepository;
        this.roomRepository = roomRepository;
        this.equipmentBookingRepository = equipmentBookingRepository;
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
        if ("CANCELLED".equalsIgnoreCase(meeting.getStatus())) {
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
        long conflictCount = meetingRepository.countRoomConflicts(
                roomId,
                meeting.getMeetingId(),
                meeting.getStartTime(),
                meeting.getEndTime()
        );

        if (conflictCount > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Room is already booked in this time range"
            );
        }

        // 7. Gán phòng cho Meeting và cập nhật trạng thái phòng
        meeting.setRoomId(room.getRoomId());
        meeting.setUpdatedAt(LocalDateTime.now());
        room.setStatus("OCCUPIED");
        roomRepository.save(room);

        // 8. Lưu xuống database
        Meeting savedMeeting = meetingRepository.save(meeting);

        // 9. Trả kết quả
        return new RoomBookingResponse(
                savedMeeting.getMeetingId(),
                savedMeeting.getRoomId(),
                savedMeeting.getStartTime(),
                savedMeeting.getEndTime(),
                "Room booked successfully"
        );
    }

    @Transactional
    public RoomBookingResponse cancelRoomBooking(String meetingId) {

        // 1. Kiểm tra cuộc họp tồn tại
        Meeting meeting = meetingRepository
                .findById(meetingId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Meeting not found: " + meetingId
                        )
                );

        // 2. Kiểm tra cuộc họp hiện có đặt phòng hay không
        if (meeting.getRoomId() == null || meeting.getRoomId().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Meeting has no room booking to cancel"
            );
        }

        // 3. Lưu thông tin phòng và khung giờ trước khi giải phóng
        String releasedRoomId = meeting.getRoomId();
        LocalDateTime startTime = meeting.getStartTime();
        LocalDateTime endTime = meeting.getEndTime();

        // 4. Hủy liên kết Meeting - Room
        meeting.setRoomId(null);
        meeting.setUpdatedAt(LocalDateTime.now());
        meetingRepository.save(meeting);

        // 5. Cập nhật trạng thái phòng về AVAILABLE nếu không còn cuộc họp active
        long activeCount = meetingRepository.findAll().stream()
                .filter(m -> !m.getMeetingId().equals(meetingId))
                .filter(m -> releasedRoomId.equalsIgnoreCase(m.getRoomId()))
                .filter(m -> "SCHEDULED".equalsIgnoreCase(m.getStatus()) || "IN_PROGRESS".equalsIgnoreCase(m.getStatus()))
                .count();
        if (activeCount == 0) {
            roomRepository.findById(releasedRoomId).ifPresent(r -> {
                r.setStatus("AVAILABLE");
                roomRepository.save(r);
            });
        }

        // 6. Giải phóng thiết bị đi kèm (nếu có)
        for (EquipmentBooking booking : equipmentBookingRepository.findAllByMeetingId(meetingId)) {
            booking.setStatus(EquipmentBookingStatus.CANCELLED);
            equipmentBookingRepository.save(booking);
        }

        // 6. Trả kết quả
        return new RoomBookingResponse(
                meeting.getMeetingId(),
                releasedRoomId,
                startTime,
                endTime,
                "Room booking cancelled successfully"
        );
    }
}