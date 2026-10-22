package com.ttcs.meetingmanagement.equipment;

import com.ttcs.meetingmanagement.dto.CreateEquipmentRequest;
import com.ttcs.meetingmanagement.dto.EquipmentAvailabilityResponse;
import com.ttcs.meetingmanagement.dto.UpdateEquipmentRequest;
import com.ttcs.meetingmanagement.exception.EquipmentException;
import com.ttcs.meetingmanagement.room.RoomRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class EquipmentService {

    private final EquipmentRepository equipmentRepository;
    private final RoomRepository roomRepository;

    public EquipmentService(
            EquipmentRepository equipmentRepository,
            RoomRepository roomRepository) {

        this.equipmentRepository = equipmentRepository;
        this.roomRepository = roomRepository;
    }

    // ==============================
    // LẤY DANH SÁCH THIẾT BỊ
    // ==============================
    public List<Equipment> getAllEquipment() {
        return equipmentRepository.findAll();
    }

    // ==============================
    // LẤY THIẾT BỊ THEO ID
    // ==============================
    public Equipment getEquipmentById(String id) {
        return equipmentRepository.findById(id)
                .orElseThrow(() ->
                        new EquipmentException(
                                "Không tìm thấy thiết bị có ID: " + id,
                                HttpStatus.NOT_FOUND
                        )
                );
    }

    // ==============================
    // THÊM THIẾT BỊ
    // ==============================
    @Transactional
    public Equipment createEquipment(CreateEquipmentRequest request) {

        String roomId = normalizeRoomId(request.getRoomId());
        validateRoomExists(roomId);

        String status = request.getStatus().trim();
        int totalQuantity = request.getTotalQuantity() != null ? request.getTotalQuantity() : 0;

        if ("AVAILABLE".equalsIgnoreCase(status) && totalQuantity == 0) {
            throw new EquipmentException(
                    "Số lượng thiết bị phải lớn hơn 0 khi ở trạng thái sẵn sàng sử dụng (AVAILABLE).",
                    HttpStatus.BAD_REQUEST
            );
        }

        Equipment equipment = new Equipment();
        equipment.setEquipmentId("EQ-" + UUID.randomUUID());
        equipment.setRoomId(roomId);
        equipment.setEquipmentName(request.getEquipmentName().trim());
        equipment.setType(request.getType().trim());
        equipment.setTotalQuantity(totalQuantity);
        equipment.setStatus(status);

        return equipmentRepository.save(equipment);
    }

    // ==============================
    // SỬA THIẾT BỊ (QUẢN TRỊ ADMIN)
    // ==============================
    @Transactional
    public Equipment updateEquipment(String id, UpdateEquipmentRequest request) {

        Equipment equipment = getEquipmentById(id);

        String roomId = normalizeRoomId(request.getRoomId());
        validateRoomExists(roomId);

        String currentStatus = equipment.getStatus();
        String newStatus = request.getStatus().trim();
        int currentTotal = equipment.getTotalQuantity() != null ? equipment.getTotalQuantity() : 0;
        int newTotal = request.getTotalQuantity() != null ? request.getTotalQuantity() : 0;

        // 1. Kiểm tra khi chuyển sang bảo trì (MAINTENANCE) hoặc ngừng sử dụng (OUT_OF_SERVICE, BROKEN)
        boolean isUnavailableStatus = newStatus.equalsIgnoreCase("MAINTENANCE")
                || newStatus.equalsIgnoreCase("OUT_OF_SERVICE")
                || newStatus.equalsIgnoreCase("BROKEN");

        if (isUnavailableStatus && !newStatus.equalsIgnoreCase(currentStatus)) {
            long activeBookings = equipmentRepository.countActiveBookings(id);
            if (activeBookings > 0) {
                throw new EquipmentException(
                        "Không thể chuyển thiết bị sang trạng thái " + newStatus +
                        " vì thiết bị đang có " + activeBookings + " lịch đặt chỗ trong tương lai.",
                        HttpStatus.CONFLICT
                );
            }
        }

        // 2. Kiểm tra khi giảm số lượng tổng: kiểm tra xem có xung đột với các lịch đặt trước không
        if (newTotal < currentTotal) {
            int maxBooked = equipmentRepository.maxBookedQuantity(id);
            if (newTotal < maxBooked) {
                throw new EquipmentException(
                        "Không thể giảm số lượng tổng xuống " + newTotal +
                        " vì đang có lịch đặt chỗ yêu cầu số lượng lên tới " + maxBooked + " trong tương lai.",
                        HttpStatus.CONFLICT
                );
            }
        }

        if ("AVAILABLE".equalsIgnoreCase(newStatus) && newTotal == 0) {
            throw new EquipmentException(
                    "Số lượng thiết bị phải lớn hơn 0 khi ở trạng thái sẵn sàng sử dụng (AVAILABLE).",
                    HttpStatus.BAD_REQUEST
            );
        }

        equipment.setRoomId(roomId);
        equipment.setEquipmentName(request.getEquipmentName().trim());
        equipment.setType(request.getType().trim());
        equipment.setTotalQuantity(newTotal);
        equipment.setStatus(newStatus);

        return equipmentRepository.save(equipment);
    }

    // ==============================
    // XÓA THIẾT BỊ
    // ==============================
    @Transactional
    public void deleteEquipment(String id) {

        Equipment equipment = getEquipmentById(id);

        long activeBookings = equipmentRepository.countActiveBookings(id);
        if (activeBookings > 0) {
            throw new EquipmentException(
                    "Không thể xóa thiết bị vì thiết bị đang có " + activeBookings + " lịch đặt chỗ trong tương lai.",
                    HttpStatus.CONFLICT
            );
        }

        equipmentRepository.delete(equipment);
    }

    // ==============================
    // KIỂM TRA ĐANG ĐƯỢC ĐẶT
    // ==============================
    public boolean isBooked(String equipmentId) {
        getEquipmentById(equipmentId);
        return equipmentRepository.countActiveBookings(equipmentId) > 0;
    }

    // ==============================
    // TÍNH SỐ LƯỢNG KHẢ DỤNG (ĐỒNG BỘ US12 & US13)
    // ==============================
    public EquipmentAvailabilityResponse getAvailability(String id) {
        Equipment equipment = getEquipmentById(id);
        int totalQuantity = equipment.getTotalQuantity() != null ? equipment.getTotalQuantity() : 0;
        int reservedQuantity = equipmentRepository.countCurrentReservedQuantity(id);

        boolean isAvailable = "AVAILABLE".equalsIgnoreCase(equipment.getStatus())
                           || "IN_USE".equalsIgnoreCase(equipment.getStatus());

        int availableQuantity = isAvailable ? Math.max(0, totalQuantity - reservedQuantity) : 0;

        return new EquipmentAvailabilityResponse(
                equipment.getEquipmentId(),
                equipment.getEquipmentName(),
                equipment.getRoomId(),
                totalQuantity,
                reservedQuantity,
                availableQuantity,
                equipment.getStatus()
        );
    }

    private String normalizeRoomId(String roomId) {
        if (roomId == null) {
            return null;
        }
        String trimmed = roomId.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void validateRoomExists(String roomId) {
        if (roomId != null && !roomRepository.existsById(roomId)) {
            throw new EquipmentException(
                    "Không tìm thấy phòng họp có mã: " + roomId,
                    HttpStatus.BAD_REQUEST
            );
        }
    }
}
