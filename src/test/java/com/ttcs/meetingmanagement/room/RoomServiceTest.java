package com.ttcs.meetingmanagement.room;

import com.ttcs.meetingmanagement.dto.CreateRoomRequest;
import com.ttcs.meetingmanagement.equipment.EquipmentRepository;
import com.ttcs.meetingmanagement.exception.RoomException;
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
class RoomServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private EquipmentRepository equipmentRepository;

    @InjectMocks
    private RoomService roomService;

    private Room testRoom;

    @BeforeEach
    void setUp() {
        testRoom = new Room();
        testRoom.setRoomId("ROOM001");
        testRoom.setRoomName("Phòng Họp 101");
        testRoom.setCapacity(20);
        testRoom.setLocation("Tầng 1");
        testRoom.setStatus("AVAILABLE");
    }

    @Test
    @DisplayName("Xóa phòng thất bại khi còn lịch họp trong tương lai")
    void testDeleteRoom_BlockedByActiveMeetings() {
        when(roomRepository.findById("ROOM001")).thenReturn(Optional.of(testRoom));
        when(roomRepository.countActiveMeetingsByRoomId("ROOM001")).thenReturn(1L);

        RoomException ex = assertThrows(RoomException.class, () ->
                roomService.deleteRoom("ROOM001")
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertTrue(ex.getMessage().contains("lịch đặt chỗ chưa kết thúc"));
        verify(roomRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Xóa phòng thất bại khi phòng còn thiết bị phân bổ")
    void testDeleteRoom_BlockedByAssignedEquipment() {
        when(roomRepository.findById("ROOM001")).thenReturn(Optional.of(testRoom));
        when(roomRepository.countActiveMeetingsByRoomId("ROOM001")).thenReturn(0L);
        when(equipmentRepository.existsByRoomId("ROOM001")).thenReturn(true);

        RoomException ex = assertThrows(RoomException.class, () ->
                roomService.deleteRoom("ROOM001")
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertTrue(ex.getMessage().contains("phòng đang có thiết bị được phân bổ"));
        verify(roomRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Xóa phòng thành công khi không còn lịch họp tương lai và không có thiết bị")
    void testDeleteRoom_Success() {
        when(roomRepository.findById("ROOM001")).thenReturn(Optional.of(testRoom));
        when(roomRepository.countActiveMeetingsByRoomId("ROOM001")).thenReturn(0L);
        when(equipmentRepository.existsByRoomId("ROOM001")).thenReturn(false);

        assertDoesNotThrow(() -> roomService.deleteRoom("ROOM001"));
        verify(roomRepository, times(1)).delete(testRoom);
    }

    @Test
    @DisplayName("Tạo phòng thất bại khi trùng mã phòng")
    void testCreateRoom_DuplicateId() {
        CreateRoomRequest req = new CreateRoomRequest();
        req.setRoomId("ROOM001");
        req.setRoomName("Phòng Mới");
        req.setCapacity(15);
        req.setStatus("AVAILABLE");

        when(roomRepository.existsById("ROOM001")).thenReturn(true);

        RoomException ex = assertThrows(RoomException.class, () ->
                roomService.createRoom(req)
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertTrue(ex.getMessage().contains("Mã phòng đã tồn tại"));
    }
}
