
package com.ttcs.meetingmanagement.room;

import com.ttcs.meetingmanagement.dto.AvailableRoomResponse;
import com.ttcs.meetingmanagement.repository.MeetingRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
public class RoomAvailabilityService {

    private final RoomService roomService;
    private final MeetingRepository meetingRepository;

    public RoomAvailabilityService(
            RoomService roomService,
            MeetingRepository meetingRepository) {

        this.roomService = roomService;
        this.meetingRepository = meetingRepository;
    }

    @Transactional(readOnly = true)
    public List<AvailableRoomResponse> getAvailableRooms(
            LocalDateTime startTime,
            LocalDateTime endTime,
            Integer minCapacity) {

        // 1. Kiem tra thoi gian
        if (startTime == null || endTime == null
                || !endTime.isAfter(startTime)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Thoi gian ket thuc phai sau thoi gian bat dau"
            );
        }

        // 2. Kiem tra suc chua
        if (minCapacity != null && minCapacity <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Suc chua phai lon hon 0"
            );
        }

        // 3. Lay danh sach phong
        List<Room> rooms;

        if (minCapacity == null) {
            rooms = roomService.getAllRooms();
        } else {
            rooms = roomService.filterByParticipantCount(
                    minCapacity
            );
        }

        // 4. Loc phong khong trung lich
        return rooms.stream()
                .filter(room -> {

                    long conflicts =
                            meetingRepository.countRoomConflicts(
                                    room.getRoomId(),
                                    null,
                                    "CANCELLED",
                                    startTime,
                                    endTime
                            );

                    return conflicts == 0;
                })

                // 5. Sap xep theo suc chua
                .sorted(
                        Comparator.comparing(Room::getCapacity)
                                .thenComparing(Room::getRoomId)
                )

                // 6. Chuyen sang DTO
                .map(AvailableRoomResponse::from)
                .toList();
    }
}
