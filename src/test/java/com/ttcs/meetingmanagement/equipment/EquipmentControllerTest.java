package com.ttcs.meetingmanagement.equipment;

import com.ttcs.meetingmanagement.dto.CreateEquipmentRequest;
import com.ttcs.meetingmanagement.dto.UpdateEquipmentRequest;
import com.ttcs.meetingmanagement.exception.EquipmentException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EquipmentControllerTest {

    @Mock
    private EquipmentService equipmentService;

    @InjectMocks
    private EquipmentController equipmentController;

    private Equipment sampleEquipment;

    @BeforeEach
    void setUp() {
        sampleEquipment = new Equipment();
        sampleEquipment.setEquipmentId("EQ-TEST");
        sampleEquipment.setEquipmentName("Microphone Sennheiser");
        sampleEquipment.setType("MICROPHONE");
        sampleEquipment.setTotalQuantity(5);
        sampleEquipment.setStatus("AVAILABLE");
    }

    @Test
    @DisplayName("Thêm thiết bị với quyền ADMIN -> Thành công")
    void testCreateEquipment_AsAdmin_Success() {
        CreateEquipmentRequest req = new CreateEquipmentRequest();
        req.setEquipmentName("Microphone Sennheiser");
        req.setType("MICROPHONE");
        req.setTotalQuantity(5);
        req.setStatus("AVAILABLE");

        when(equipmentService.createEquipment(any(CreateEquipmentRequest.class))).thenReturn(sampleEquipment);

        ResponseEntity<Equipment> response = equipmentController.createEquipment("ADMIN", null, req);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("EQ-TEST", response.getBody().getEquipmentId());
    }

    @Test
    @DisplayName("Thêm thiết bị với vai trò USER -> Bị chặn 403 FORBIDDEN")
    void testCreateEquipment_AsUser_ThrowsForbidden() {
        CreateEquipmentRequest req = new CreateEquipmentRequest();
        req.setEquipmentName("Microphone Sennheiser");
        req.setType("MICROPHONE");
        req.setTotalQuantity(5);
        req.setStatus("AVAILABLE");

        EquipmentException ex = assertThrows(EquipmentException.class, () ->
                equipmentController.createEquipment("USER", null, req)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertTrue(ex.getMessage().contains("Quản trị viên (Admin)"));
        verify(equipmentService, never()).createEquipment(any());
    }

    @Test
    @DisplayName("Cập nhật thiết bị với vai trò USER -> Bị chặn 403 FORBIDDEN")
    void testUpdateEquipment_AsUser_ThrowsForbidden() {
        UpdateEquipmentRequest req = new UpdateEquipmentRequest();
        req.setEquipmentName("Microphone Sennheiser Pro");
        req.setType("MICROPHONE");
        req.setTotalQuantity(4);
        req.setStatus("AVAILABLE");

        EquipmentException ex = assertThrows(EquipmentException.class, () ->
                equipmentController.updateEquipment("USER", null, "EQ-TEST", req)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verify(equipmentService, never()).updateEquipment(any(), any());
    }

    @Test
    @DisplayName("Xóa thiết bị với vai trò USER -> Bị chặn 403 FORBIDDEN")
    void testDeleteEquipment_AsUser_ThrowsForbidden() {
        EquipmentException ex = assertThrows(EquipmentException.class, () ->
                equipmentController.deleteEquipment("USER", null, "EQ-TEST")
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verify(equipmentService, never()).deleteEquipment(any());
    }

    @Test
    @DisplayName("Không truyền header quyền -> Cho phép xử lý (tương thích backward)")
    void testCreateEquipment_NoHeader_Allowed() {
        CreateEquipmentRequest req = new CreateEquipmentRequest();
        req.setEquipmentName("Microphone Sennheiser");
        req.setType("MICROPHONE");
        req.setTotalQuantity(5);
        req.setStatus("AVAILABLE");

        when(equipmentService.createEquipment(any(CreateEquipmentRequest.class))).thenReturn(sampleEquipment);

        ResponseEntity<Equipment> response = equipmentController.createEquipment(null, null, req);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(equipmentService, times(1)).createEquipment(any());
    }
}
