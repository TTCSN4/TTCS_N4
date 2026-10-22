package com.ttcs.meetingmanagement.equipment;

import com.ttcs.meetingmanagement.dto.CreateEquipmentRequest;
import com.ttcs.meetingmanagement.dto.EquipmentAvailabilityResponse;
import com.ttcs.meetingmanagement.dto.UpdateEquipmentRequest;
import com.ttcs.meetingmanagement.exception.EquipmentException;
import com.ttcs.meetingmanagement.room.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EquipmentServiceTest {

    @Mock
    private EquipmentRepository equipmentRepository;

    @Mock
    private RoomRepository roomRepository;

    @InjectMocks
    private EquipmentService equipmentService;

    private Equipment testEquipment;

    @BeforeEach
    void setUp() {
        testEquipment = new Equipment();
        testEquipment.setEquipmentId("EQ-001");
        testEquipment.setEquipmentName("Máy chiếu Panasonic");
        testEquipment.setType("PROJECTOR");
        testEquipment.setTotalQuantity(10);
        testEquipment.setStatus("AVAILABLE");
        testEquipment.setRoomId("ROOM001");
    }

    @Test
    @DisplayName("Thêm thiết bị thành công khi phòng họp tồn tại")
    void testCreateEquipment_Success() {
        CreateEquipmentRequest req = new CreateEquipmentRequest();
        req.setEquipmentName("Loa Bluetooth");
        req.setType("SPEAKER");
        req.setTotalQuantity(5);
        req.setStatus("AVAILABLE");
        req.setRoomId("ROOM001");

        when(roomRepository.existsById("ROOM001")).thenReturn(true);
        when(equipmentRepository.save(any(Equipment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Equipment result = equipmentService.createEquipment(req);

        assertNotNull(result);
        assertEquals("Loa Bluetooth", result.getEquipmentName());
        assertEquals("ROOM001", result.getRoomId());
        assertEquals(5, result.getTotalQuantity());
    }

    @Test
    @DisplayName("Thêm thiết bị thất bại khi mã phòng không tồn tại")
    void testCreateEquipment_RoomNotFound() {
        CreateEquipmentRequest req = new CreateEquipmentRequest();
        req.setEquipmentName("Loa Bluetooth");
        req.setType("SPEAKER");
        req.setTotalQuantity(5);
        req.setStatus("AVAILABLE");
        req.setRoomId("ROOM_UNKNOWN");

        when(roomRepository.existsById("ROOM_UNKNOWN")).thenReturn(false);

        EquipmentException ex = assertThrows(EquipmentException.class, () ->
                equipmentService.createEquipment(req)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("Không tìm thấy phòng họp"));
    }

    @Test
    @DisplayName("Cập nhật thông tin thiết bị (tên, loại, tăng số lượng) thành công")
    void testUpdateEquipment_Success() {
        UpdateEquipmentRequest req = new UpdateEquipmentRequest();
        req.setEquipmentName("Máy chiếu Panasonic Pro");
        req.setType("PROJECTOR");
        req.setTotalQuantity(15); // Tăng số lượng
        req.setStatus("AVAILABLE");
        req.setRoomId("ROOM001");

        when(equipmentRepository.findById("EQ-001")).thenReturn(Optional.of(testEquipment));
        when(roomRepository.existsById("ROOM001")).thenReturn(true);
        when(equipmentRepository.save(any(Equipment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Equipment updated = equipmentService.updateEquipment("EQ-001", req);

        assertEquals("Máy chiếu Panasonic Pro", updated.getEquipmentName());
        assertEquals(15, updated.getTotalQuantity());
    }

    @Test
    @DisplayName("Chuyển trạng thái sang MAINTENANCE bị từ chối nếu đang có đặt chỗ trong tương lai")
    void testUpdateEquipment_SwitchToMaintenanceBlockedByActiveBookings() {
        UpdateEquipmentRequest req = new UpdateEquipmentRequest();
        req.setEquipmentName("Máy chiếu Panasonic");
        req.setType("PROJECTOR");
        req.setTotalQuantity(10);
        req.setStatus("MAINTENANCE");
        req.setRoomId("ROOM001");

        when(equipmentRepository.findById("EQ-001")).thenReturn(Optional.of(testEquipment));
        when(roomRepository.existsById("ROOM001")).thenReturn(true);
        when(equipmentRepository.countActiveBookings("EQ-001")).thenReturn(2L);

        EquipmentException ex = assertThrows(EquipmentException.class, () ->
                equipmentService.updateEquipment("EQ-001", req)
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertTrue(ex.getMessage().contains("lịch đặt chỗ trong tương lai"));
    }

    @Test
    @DisplayName("Giảm số lượng tổng xuống dưới số lượng đã được đặt bị từ chối")
    void testUpdateEquipment_ReduceQuantityBelowMaxBooked() {
        UpdateEquipmentRequest req = new UpdateEquipmentRequest();
        req.setEquipmentName("Máy chiếu Panasonic");
        req.setType("PROJECTOR");
        req.setTotalQuantity(4); // Giảm từ 10 xuống 4
        req.setStatus("AVAILABLE");
        req.setRoomId("ROOM001");

        when(equipmentRepository.findById("EQ-001")).thenReturn(Optional.of(testEquipment));
        when(roomRepository.existsById("ROOM001")).thenReturn(true);
        when(equipmentRepository.maxBookedQuantity("EQ-001")).thenReturn(6); // Đang có đặt 6 cái

        EquipmentException ex = assertThrows(EquipmentException.class, () ->
                equipmentService.updateEquipment("EQ-001", req)
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertTrue(ex.getMessage().contains("Không thể giảm số lượng tổng"));
    }

    @Test
    @DisplayName("Xóa thiết bị bị từ chối nếu có lịch đặt trong tương lai")
    void testDeleteEquipment_BlockedByActiveBookings() {
        when(equipmentRepository.findById("EQ-001")).thenReturn(Optional.of(testEquipment));
        when(equipmentRepository.countActiveBookings("EQ-001")).thenReturn(1L);

        EquipmentException ex = assertThrows(EquipmentException.class, () ->
                equipmentService.deleteEquipment("EQ-001")
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(equipmentRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Tính toán số lượng khả dụng chính xác cho US12/US13")
    void testGetAvailability() {
        when(equipmentRepository.findById("EQ-001")).thenReturn(Optional.of(testEquipment));
        when(equipmentRepository.countCurrentReservedQuantity("EQ-001")).thenReturn(3);

        EquipmentAvailabilityResponse response = equipmentService.getAvailability("EQ-001");

        assertNotNull(response);
        assertEquals(10, response.getTotalQuantity());
        assertEquals(3, response.getBookedQuantity());
        assertEquals(7, response.getAvailableQuantity()); // 10 - 3 = 7
    }

    @Test
    @DisplayName("Số lượng khả dụng bằng 0 khi thiết bị đang ở trạng thái MAINTENANCE")
    void testGetAvailability_MaintenanceZero() {
        testEquipment.setStatus("MAINTENANCE");
        when(equipmentRepository.findById("EQ-001")).thenReturn(Optional.of(testEquipment));
        when(equipmentRepository.countCurrentReservedQuantity("EQ-001")).thenReturn(0);

        EquipmentAvailabilityResponse response = equipmentService.getAvailability("EQ-001");

        assertNotNull(response);
        assertEquals(0, response.getAvailableQuantity());
    }
}
