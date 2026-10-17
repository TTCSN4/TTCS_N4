package com.ttcs.meetingmanagement.room;

import com.ttcs.meetingmanagement.dto.CreateRoomRequest;
import com.ttcs.meetingmanagement.dto.UpdateRoomRequest;
import com.ttcs.meetingmanagement.exception.RoomException;
import com.ttcs.meetingmanagement.model.Meeting;
import com.ttcs.meetingmanagement.model.MeetingParticipant;
import com.ttcs.meetingmanagement.repository.MeetingParticipantRepository;
import com.ttcs.meetingmanagement.repository.MeetingRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class RoomService {

    public static final Set<String> VALID_STATUSES = Set.of(
            "AVAILABLE",
            "ACTIVE",
            "MAINTENANCE",
            "INACTIVE",
            "UNAVAILABLE"
    );

    public static final Set<String> BOOKABLE_STATUSES = Set.of(
            "AVAILABLE",
            "ACTIVE"
    );

    private final RoomRepository roomRepository;
    private final MeetingRepository meetingRepository;
    private final MeetingParticipantRepository meetingParticipantRepository;

    public RoomService(
            RoomRepository roomRepository,
            MeetingRepository meetingRepository,
            MeetingParticipantRepository meetingParticipantRepository) {

        this.roomRepository = roomRepository;
        this.meetingRepository = meetingRepository;
        this.meetingParticipantRepository = meetingParticipantRepository;
    }

    // =========================
    // US11 - QUAN LY PHONG
    // =========================

    // Lay toan bo phong
    public List<Room> getAllRooms() {
        return roomRepository.findAll();
    }

    // Lay phong theo ma
    public Room getRoomById(String roomId) {
        if (roomId == null || roomId.isBlank()) {
            throw new RoomException(
                    "Mã phòng không được để trống",
                    HttpStatus.BAD_REQUEST
            );
        }

        return roomRepository.findById(roomId.trim())
                .orElseThrow(() ->
                        new RoomException(
                                "Không tìm thấy phòng có mã: " + roomId.trim(),
                                HttpStatus.NOT_FOUND
                        )
                );
    }

    // US11.1 - Them phong moi
    @Transactional
    public Room createRoom(CreateRoomRequest request) {

        if (request == null) {
            throw new RoomException("Dữ liệu tạo phòng không được để trống", HttpStatus.BAD_REQUEST);
        }

        if (request.getRoomId() == null || request.getRoomId().isBlank()) {
            throw new RoomException("Mã phòng không được để trống", HttpStatus.BAD_REQUEST);
        }

        if (request.getRoomName() == null || request.getRoomName().isBlank()) {
            throw new RoomException("Tên phòng không được để trống", HttpStatus.BAD_REQUEST);
        }

        String roomId = request.getRoomId().trim();
        String roomName = request.getRoomName().trim();

        // Kiem tra trung ma phong
        if (roomRepository.existsById(roomId)) {
            throw new RoomException(
                    "Mã phòng đã tồn tại: " + roomId,
                    HttpStatus.CONFLICT
            );
        }

        // Kiem tra trung ten phong (case-insensitive)
        if (roomRepository.existsByRoomNameIgnoreCase(roomName)) {
            throw new RoomException(
                    "Tên phòng đã tồn tại: " + roomName,
                    HttpStatus.CONFLICT
            );
        }

        // Kiem tra suc chua
        if (request.getCapacity() == null || request.getCapacity() <= 0) {
            throw new RoomException(
                    "Sức chứa phòng phải lớn hơn 0",
                    HttpStatus.BAD_REQUEST
            );
        }

        // Kiem tra trang thai phong
        if (request.getStatus() == null || request.getStatus().isBlank()) {
            throw new RoomException(
                    "Trạng thái phòng không được để trống",
                    HttpStatus.BAD_REQUEST
            );
        }

        String normStatus = request.getStatus().trim().toUpperCase();
        if (!VALID_STATUSES.contains(normStatus)) {
            throw new RoomException(
                    "Trạng thái phòng không hợp lệ: " + request.getStatus(),
                    HttpStatus.BAD_REQUEST
            );
        }

        Room room = new Room();
        room.setRoomId(roomId);
        room.setRoomName(roomName);
        room.setCapacity(request.getCapacity());

        room.setLocation(
                request.getLocation() != null && !request.getLocation().isBlank()
                        ? request.getLocation().trim()
                        : null
        );

        room.setQrCode(
                request.getQrCode() != null && !request.getQrCode().isBlank()
                        ? request.getQrCode().trim()
                        : null
        );

        room.setStatus(normStatus);

        return roomRepository.save(room);
    }

    // US11.2, US11.3, US11.4 - Cap nhat phong
    @Transactional
    public Room updateRoom(
            String roomId,
            UpdateRoomRequest request) {

        if (roomId == null || roomId.isBlank()) {
            throw new RoomException("Mã phòng không được để trống", HttpStatus.BAD_REQUEST);
        }

        if (request == null) {
            throw new RoomException("Dữ liệu cập nhật phòng không được để trống", HttpStatus.BAD_REQUEST);
        }

        String trimmedRoomId = roomId.trim();

        // Row-level lock PESSIMISTIC_WRITE
        Room room = roomRepository.findLockedById(trimmedRoomId)
                .orElseThrow(() ->
                        new RoomException(
                                "Không tìm thấy phòng có mã: " + trimmedRoomId,
                                HttpStatus.NOT_FOUND
                        )
                );

        if (request.getRoomName() == null || request.getRoomName().isBlank()) {
            throw new RoomException("Tên phòng không được để trống", HttpStatus.BAD_REQUEST);
        }

        String roomName = request.getRoomName().trim();

        // Kiem tra trung ten phong voi phong khac
        if (roomRepository.existsByRoomNameIgnoreCaseAndRoomIdNot(
                roomName,
                trimmedRoomId
        )) {
            throw new RoomException(
                    "Tên phòng đã tồn tại: " + roomName,
                    HttpStatus.CONFLICT
            );
        }

        // Kiem tra suc chua hop le
        if (request.getCapacity() == null || request.getCapacity() <= 0) {
            throw new RoomException(
                    "Sức chứa phòng phải lớn hơn 0",
                    HttpStatus.BAD_REQUEST
            );
        }

        // Kiem tra trang thai phong hop le
        if (request.getStatus() == null || request.getStatus().isBlank()) {
            throw new RoomException(
                    "Trạng thái phòng không được để trống",
                    HttpStatus.BAD_REQUEST
            );
        }

        String normStatus = request.getStatus().trim().toUpperCase();
        if (!VALID_STATUSES.contains(normStatus)) {
            throw new RoomException(
                    "Trạng thái phòng không hợp lệ: " + request.getStatus(),
                    HttpStatus.BAD_REQUEST
            );
        }

        // US11.4: Kiem tra suc chua voi cac cuoc hop tuong lai
        int newCapacity = request.getCapacity();
        if (newCapacity < room.getCapacity()) {
            validateCapacityForUpcomingMeetings(trimmedRoomId, newCapacity);
        }

        room.setRoomName(roomName);
        room.setCapacity(newCapacity);

        room.setLocation(
                request.getLocation() != null && !request.getLocation().isBlank()
                        ? request.getLocation().trim()
                        : null
        );

        room.setQrCode(
                request.getQrCode() != null && !request.getQrCode().isBlank()
                        ? request.getQrCode().trim()
                        : null
        );

        room.setStatus(normStatus);

        return roomRepository.save(room);
    }

    // US11.4 helper: Kiem tra cac cuoc hop tuong lai khong bi vuot qua suc chua moi
    private void validateCapacityForUpcomingMeetings(String roomId, int newCapacity) {
        LocalDateTime now = LocalDateTime.now();
        List<Meeting> upcomingMeetings =
                meetingRepository.findUpcomingActiveMeetingsByRoomId(roomId, now);

        for (Meeting meeting : upcomingMeetings) {
            Set<String> attendeeIds = new HashSet<>();

            // 1. Nguoi to chuc
            if (meeting.getOrganizerId() != null && !meeting.getOrganizerId().isBlank()) {
                attendeeIds.add(meeting.getOrganizerId().trim());
            }

            // 2. Danh sach nguoi tham du trong meeting_participant
            List<MeetingParticipant> participants =
                    meetingParticipantRepository.findByMeetingId(meeting.getMeetingId());

            if (participants != null) {
                for (MeetingParticipant p : participants) {
                    if (p.getUserId() != null && !p.getUserId().isBlank()) {
                        String pStatus = p.getStatus() != null ? p.getStatus().trim().toUpperCase() : "";
                        // Loai bo nguoi tham du da tu choi hoac bi xoa
                        if (!"DECLINED".equals(pStatus) && !"REMOVED".equals(pStatus)) {
                            attendeeIds.add(p.getUserId().trim());
                        }
                    }
                }
            }

            int attendeeCount = attendeeIds.size();
            if (attendeeCount > newCapacity) {
                throw new RoomException(
                        String.format(
                                "Không thể giảm sức chứa xuống %d vì cuộc họp '%s' có %d người tham gia",
                                newCapacity,
                                meeting.getTitle(),
                                attendeeCount
                        ),
                        HttpStatus.CONFLICT
                );
            }
        }
    }

    // Xoa phong
    @Transactional
    public void deleteRoom(String roomId) {
        if (roomId == null || roomId.isBlank()) {
            throw new RoomException("Mã phòng không được để trống", HttpStatus.BAD_REQUEST);
        }

        Room room = getRoomById(roomId);

        long meetingCount =
                roomRepository.countMeetingsByRoomId(room.getRoomId());

        if (meetingCount > 0) {
            throw new RoomException(
                    "Không thể xóa phòng vì phòng đang có lịch đặt",
                    HttpStatus.CONFLICT
            );
        }

        roomRepository.delete(room);
    }

    // US10 - SUC CHUA PHONG
    public List<Room> filterByParticipantCount(Integer participantCount) {

        if (participantCount == null || participantCount <= 0) {
            throw new RoomException(
                    "Số người tham dự phải lớn hơn 0",
                    HttpStatus.BAD_REQUEST
            );
        }

        return roomRepository
                .findByCapacityGreaterThanEqualOrderByCapacityAsc(
                        participantCount
                );
    }

    // Helper: kiem tra trang thai phong co the nhan lich dat hay khong
    public static boolean isRoomBookable(String status) {
        if (status == null) {
            return false;
        }
        return BOOKABLE_STATUSES.contains(status.trim().toUpperCase());
    }

    public static boolean isRoomBookable(Room room) {
        if (room == null || room.getStatus() == null) {
            return false;
        }
        return isRoomBookable(room.getStatus());
    }
}
