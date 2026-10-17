package com.ttcs.meetingmanagement.room;

import com.ttcs.meetingmanagement.dto.AvailableRoomResponse;
import com.ttcs.meetingmanagement.dto.RoomAvailabilitySummaryResponse;
import com.ttcs.meetingmanagement.dto.RoomScheduleStatusResponse;
import com.ttcs.meetingmanagement.model.MeetingStatus;
import com.ttcs.meetingmanagement.repository.MeetingRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
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
            OffsetDateTime startTime,
            OffsetDateTime endTime,
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

        // 4. Loc phong khong trung lich va phong phai AVAILABLE
        return rooms.stream()
                .filter(room -> "AVAILABLE".equalsIgnoreCase(room.getStatus()))
                .filter(room -> {

                    long conflicts =
                            meetingRepository.countRoomConflicts(
                                    room.getRoomId(),
                                    null,
                                    MeetingStatus.CANCELLED,
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

    @Transactional(readOnly = true)
    public List<AvailableRoomResponse> getAvailableRooms(
            LocalDateTime startTime,
            LocalDateTime endTime,
            Integer minCapacity) {
        OffsetDateTime start = startTime != null ? startTime.atZone(ZoneId.systemDefault()).toOffsetDateTime() : null;
        OffsetDateTime end = endTime != null ? endTime.atZone(ZoneId.systemDefault()).toOffsetDateTime() : null;
        return getAvailableRooms(start, end, minCapacity);
    }

    @Transactional(readOnly = true)
    public RoomAvailabilitySummaryResponse getRoomAvailabilityOverview(
            OffsetDateTime startTime,
            OffsetDateTime endTime,
            Integer minCapacity) {

        List<Room> allRooms = roomService.getAllRooms();

        if (startTime == null || endTime == null || !endTime.isAfter(startTime)) {
            List<RoomScheduleStatusResponse> initialStatuses = allRooms.stream()
                    .map(room -> {
                        boolean isAvail = "AVAILABLE".equalsIgnoreCase(room.getStatus());
                        String reason = isAvail ? "Chọn khung giờ để kiểm tra" : ("Phòng " + formatStatus(room.getStatus()));
                        return new RoomScheduleStatusResponse(
                                room.getRoomId(),
                                room.getRoomName(),
                                room.getCapacity(),
                                room.getLocation(),
                                room.getStatus(),
                                isAvail,
                                reason
                        );
                    })
                    .sorted(Comparator.comparing(RoomScheduleStatusResponse::isAvailable).reversed()
                            .thenComparing(RoomScheduleStatusResponse::capacity)
                            .thenComparing(RoomScheduleStatusResponse::roomId))
                    .toList();

            int availableCount = (int) initialStatuses.stream().filter(RoomScheduleStatusResponse::isAvailable).count();
            int maintenanceCount = allRooms.size() - availableCount;
            return new RoomAvailabilitySummaryResponse(allRooms.size(), availableCount, 0, maintenanceCount, initialStatuses);
        }

        int busyCount = 0;
        int maintenanceCount = 0;
        List<RoomScheduleStatusResponse> roomStatuses = new ArrayList<>();

        for (Room room : allRooms) {
            boolean available = true;
            String reason = null;

            // 1. Kiểm tra trạng thái hoạt động của phòng trong cơ sở dữ liệu
            if (!"AVAILABLE".equalsIgnoreCase(room.getStatus())) {
                available = false;
                reason = "Phòng " + formatStatus(room.getStatus());
                maintenanceCount++;
            }
            // 2. Kiểm tra sức chứa (nếu có yêu cầu lọc theo số người)
            else if (minCapacity != null && minCapacity > 0 && room.getCapacity() < minCapacity) {
                available = false;
                reason = "Không đủ sức chứa (yêu cầu tối thiểu " + minCapacity + " người)";
            }
            // 3. Kiểm tra lịch trùng giờ với các cuộc họp thực tế trong DB
            else {
                long conflicts = meetingRepository.countRoomConflicts(
                        room.getRoomId(),
                        null,
                        MeetingStatus.CANCELLED,
                        startTime,
                        endTime
                );
                if (conflicts > 0) {
                    available = false;
                    reason = "Đã có lịch họp trùng giờ";
                    busyCount++;
                }
            }

            roomStatuses.add(new RoomScheduleStatusResponse(
                    room.getRoomId(),
                    room.getRoomName(),
                    room.getCapacity(),
                    room.getLocation(),
                    room.getStatus(),
                    available,
                    reason
            ));
        }

        // Sắp xếp: Các phòng khả dụng lên trước, sau đó theo sức chứa
        roomStatuses.sort(
                Comparator.comparing(RoomScheduleStatusResponse::isAvailable).reversed()
                        .thenComparing(RoomScheduleStatusResponse::capacity)
                        .thenComparing(RoomScheduleStatusResponse::roomId)
        );

        int availableCount = (int) roomStatuses.stream().filter(RoomScheduleStatusResponse::isAvailable).count();

        return new RoomAvailabilitySummaryResponse(
                allRooms.size(),
                availableCount,
                busyCount,
                maintenanceCount,
                roomStatuses
        );
    }

    private String formatStatus(String status) {
        if (status == null) return "không khả dụng";
        return switch (status.toUpperCase()) {
            case "MAINTENANCE" -> "đang bảo trì";
            case "INACTIVE", "DISABLED" -> "ngừng sử dụng";
            default -> "không khả dụng (" + status + ")";
        };
    }
}
