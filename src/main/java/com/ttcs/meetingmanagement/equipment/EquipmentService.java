package com.ttcs.meetingmanagement.equipment;

import com.ttcs.meetingmanagement.dto.CreateEquipmentRequest;
import com.ttcs.meetingmanagement.dto.UpdateEquipmentRequest;
import com.ttcs.meetingmanagement.exception.EquipmentException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class EquipmentService {

    private final EquipmentRepository equipmentRepository;

    public EquipmentService(
            EquipmentRepository equipmentRepository) {

        this.equipmentRepository = equipmentRepository;
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
    public Equipment createEquipment(
            CreateEquipmentRequest request) {

        Equipment equipment = new Equipment();

        equipment.setEquipmentId(
                "EQ-" + UUID.randomUUID()
        );

        equipment.setRoomId(
                request.getRoomId()
        );

        equipment.setEquipmentName(
                request.getEquipmentName().trim()
        );

        equipment.setType(
                request.getType().trim()
        );

        equipment.setTotalQuantity(
                request.getTotalQuantity()
        );

        equipment.setStatus(
                request.getStatus().trim()
        );

        return equipmentRepository.save(equipment);
    }

    // ==============================
    // SỬA THIẾT BỊ
    // ==============================
    @Transactional
    public Equipment updateEquipment(
            String id,
            UpdateEquipmentRequest request) {

        Equipment equipment = getEquipmentById(id);

        if (isBooked(id)) {

            throw new EquipmentException(
                    "Không thể sửa thiết bị vì thiết bị đang được đặt.",
                    HttpStatus.CONFLICT
            );
        }

        equipment.setRoomId(
                request.getRoomId()
        );

        equipment.setEquipmentName(
                request.getEquipmentName().trim()
        );

        equipment.setType(
                request.getType().trim()
        );

        equipment.setTotalQuantity(
                request.getTotalQuantity()
        );

        equipment.setStatus(
                request.getStatus().trim()
        );

        return equipmentRepository.save(equipment);
    }

    // ==============================
    // XÓA THIẾT BỊ
    // ==============================
    @Transactional
    public void deleteEquipment(String id) {

        Equipment equipment = getEquipmentById(id);

        if (isBooked(id)) {

            throw new EquipmentException(
                    "Không thể xóa thiết bị vì thiết bị đang được đặt.",
                    HttpStatus.CONFLICT
            );
        }

        equipmentRepository.delete(equipment);
    }
    // ==============================
    // LẤY DANH SÁCH THIẾT BỊ THEO ROOM ID
    // ==============================
    public List<Equipment> getEquipmentByRoomId(String roomId) {
        return equipmentRepository.findByRoomId(roomId);
    }

    // ==============================
    // KIỂM TRA ĐANG ĐƯỢC ĐẶT
    // ==============================
    public boolean isBooked(String equipmentId) {

        getEquipmentById(equipmentId);

        return equipmentRepository
                .countActiveBookings(equipmentId) > 0;
    }
}
