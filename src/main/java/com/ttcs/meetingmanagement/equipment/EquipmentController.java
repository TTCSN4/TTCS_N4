package com.ttcs.meetingmanagement.equipment;

import com.ttcs.meetingmanagement.dto.CreateEquipmentRequest;
import com.ttcs.meetingmanagement.dto.UpdateEquipmentRequest;

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

    public EquipmentController(EquipmentService equipmentService) {
        this.equipmentService = equipmentService;
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
            @Valid @RequestBody CreateEquipmentRequest request) {

        Equipment equipment =
                equipmentService.createEquipment(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(equipment);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Equipment> updateEquipment(
            @PathVariable String id,
            @Valid @RequestBody UpdateEquipmentRequest request) {

        return ResponseEntity.ok(
                equipmentService.updateEquipment(id, request)
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEquipment(
            @PathVariable String id) {

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
}
