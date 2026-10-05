package com.ttcs.meetingmanagement.room;

import com.ttcs.meetingmanagement.dto.CreateRoomRequest;
import com.ttcs.meetingmanagement.dto.UpdateRoomRequest;
import com.ttcs.meetingmanagement.exception.RoomException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RoomService {

    private final RoomRepository roomRepository;

    public RoomService(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }


    // =========================
    // US11 - QUẢN LÝ PHÒNG
    // =========================

    // Lấy toàn bộ phòng
    public List<Room> getAllRooms() {
        return roomRepository.findAll();
    }


    // Lấy phòng theo mã
    public Room getRoomById(String roomId) {

        return roomRepository.findById(roomId)
                .orElseThrow(() ->
                        new RoomException(
                                "Không tìm thấy phòng có mã: " + roomId
                        )
                );
    }


    // Thêm phòng
    @Transactional
    public Room createRoom(CreateRoomRequest request) {

        String roomId = request.getRoomId().trim();
        String roomName = request.getRoomName().trim();

        // Kiểm tra trùng mã phòng
        if (roomRepository.existsById(roomId)) {
            throw new RoomException(
                    "Mã phòng đã tồn tại: " + roomId
            );
        }

        // Kiểm tra trùng tên phòng
        if (roomRepository.existsByRoomNameIgnoreCase(roomName)) {
            throw new RoomException(
                    "Tên phòng đã tồn tại: " + roomName
            );
        }

        // Kiểm tra sức chứa
        if (request.getCapacity() == null ||
                request.getCapacity() <= 0) {

            throw new RoomException(
                    "Sức chứa phòng phải lớn hơn 0"
            );
        }

        Room room = new Room();

        room.setRoomId(roomId);
        room.setRoomName(roomName);
        room.setCapacity(request.getCapacity());

        room.setLocation(
                request.getLocation() != null
                        ? request.getLocation().trim()
                        : null
        );

        room.setQrCode(
                request.getQrCode() != null
                        ? request.getQrCode().trim()
                        : null
        );

        room.setStatus(request.getStatus().trim());

        return roomRepository.save(room);
    }


    // Cập nhật phòng
    @Transactional
    public Room updateRoom(
            String roomId,
            UpdateRoomRequest request) {

        Room room = getRoomById(roomId);

        String roomName = request.getRoomName().trim();

        // Kiểm tra tên phòng mới có bị trùng phòng khác không
        if (roomRepository
                .existsByRoomNameIgnoreCaseAndRoomIdNot(
                        roomName,
                        roomId
                )) {

            throw new RoomException(
                    "Tên phòng đã tồn tại: " + roomName
            );
        }

        if (request.getCapacity() == null ||
                request.getCapacity() <= 0) {

            throw new RoomException(
                    "Sức chứa phòng phải lớn hơn 0"
            );
        }

        room.setRoomName(roomName);
        room.setCapacity(request.getCapacity());

        room.setLocation(
                request.getLocation() != null
                        ? request.getLocation().trim()
                        : null
        );

        room.setQrCode(
                request.getQrCode() != null
                        ? request.getQrCode().trim()
                        : null
        );

        room.setStatus(request.getStatus().trim());

        return roomRepository.save(room);
    }


    // Xóa phòng
    @Transactional
    public void deleteRoom(String roomId) {

        Room room = getRoomById(roomId);

        long meetingCount =
                roomRepository.countMeetingsByRoomId(roomId);

        if (meetingCount > 0) {
            throw new RoomException(
                    "Không thể xóa phòng vì phòng đang có lịch đặt"
            );
        }

        roomRepository.delete(room);
    }


    // =========================
    // US10 - SỨC CHỨA PHÒNG
    // =========================

    public List<Room> filterByParticipantCount(
            Integer participantCount) {

        if (participantCount == null ||
                participantCount <= 0) {

            throw new RoomException(
                    "Số người tham dự phải lớn hơn 0"
            );
        }

        return roomRepository
                .findByCapacityGreaterThanEqualOrderByCapacityAsc(
                        participantCount
                );
    }
}