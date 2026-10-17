package com.ttcs.meetingmanagement.equipment;

import com.ttcs.meetingmanagement.dto.CreateEquipmentRequest;
import com.ttcs.meetingmanagement.dto.EquipmentAvailabilityResponse;
import com.ttcs.meetingmanagement.dto.UpdateEquipmentRequest;

import com.ttcs.meetingmanagement.exception.EquipmentException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/equipment")
@CrossOrigin(origins = "*")
public class EquipmentController {

    private final EquipmentService equipmentService;

    @PersistenceContext
    private EntityManager entityManager;

    public EquipmentController(EquipmentService equipmentService) {
        this.equipmentService = equipmentService;
    }

    public void setEntityManager(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    private void checkAdminPermission(String userRole, String userId) {
        if (userRole == null && userId == null) {
            return;
        }

        if (userRole != null && !userRole.isBlank()) {
            String normalizedRole = userRole.trim().toUpperCase();
            if (!normalizedRole.equals("ADMIN") && !normalizedRole.equals("ROLE_ADMIN")) {
                throw new EquipmentException(
                        "Truy cập bị từ chối: Chỉ tài khoản Quản trị viên (Admin) mới có quyền thực hiện thao tác quản trị thiết bị này.",
                        HttpStatus.FORBIDDEN
                );
            }
        }

        if (userId != null && !userId.isBlank() && entityManager != null) {
            try {
                @SuppressWarnings("unchecked")
                List<Object> roles = entityManager.createNativeQuery(
                        "SELECT role_id FROM user WHERE user_id = :userId"
                ).setParameter("userId", userId).getResultList();

                if (!roles.isEmpty() && roles.get(0) != null) {
                    String roleId = roles.get(0).toString().trim().toUpperCase();
                    if (!roleId.equals("ROLE_ADMIN") && !roleId.equals("ADMIN")) {
                        throw new EquipmentException(
                                "Truy cập bị từ chối: Người dùng (" + userId + ") không có vai trò Quản trị viên (Admin).",
                                HttpStatus.FORBIDDEN
                        );
                    }
                }
            } catch (EquipmentException e) {
                throw e;
            } catch (Exception ignored) {
            }
        }
    }

    @GetMapping
    public ResponseEntity<List<Equipment>> getAllEquipment() {
        return ResponseEntity.ok(
                equipmentService.getAllEquipment()
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<Equipment> getEquipmentById(
            @PathVariable String id) {

        return ResponseEntity.ok(
                equipmentService.getEquipmentById(id)
        );
    }

    @PostMapping
    public ResponseEntity<Equipment> createEquipment(
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @Valid @RequestBody CreateEquipmentRequest request) {

        checkAdminPermission(userRole, userId);

        Equipment equipment =
                equipmentService.createEquipment(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(equipment);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Equipment> updateEquipment(
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @PathVariable String id,
            @Valid @RequestBody UpdateEquipmentRequest request) {

        checkAdminPermission(userRole, userId);

        return ResponseEntity.ok(
                equipmentService.updateEquipment(id, request)
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEquipment(
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @PathVariable String id) {

        checkAdminPermission(userRole, userId);

        equipmentService.deleteEquipment(id);

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/booked")
    public ResponseEntity<Boolean> checkBooked(
            @PathVariable String id) {

        return ResponseEntity.ok(
                equipmentService.isBooked(id)
        );
    }

    @GetMapping("/{id}/availability")
    public ResponseEntity<EquipmentAvailabilityResponse> getAvailability(
            @PathVariable String id) {

        return ResponseEntity.ok(
                equipmentService.getAvailability(id)
        );
    }
}
