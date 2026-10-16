package com.ttcs.meetingmanagement.room;

import com.ttcs.meetingmanagement.exception.RoomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class RoomCapacityServiceTest {

    @Autowired
    private RoomService roomService;

    @Autowired
    private RoomController roomController;

    @Autowired
    private RoomRepository roomRepository;

    @BeforeEach
    void setUp() {
        roomRepository.deleteAll();

        createTestRoom("R-01", "Phòng Họp Nhỏ", 6, "AVAILABLE");
        createTestRoom("R-02", "Phòng Họp Vừa", 12, "AVAILABLE");
        createTestRoom("R-03", "Phòng Họp Lớn", 25, "AVAILABLE");
        createTestRoom("R-04", "Hội Trường", 100, "MAINTENANCE");
    }

    private void createTestRoom(String id, String name, int capacity, String status) {
        Room room = new Room();
        room.setRoomId(id);
        room.setRoomName(name);
        room.setCapacity(capacity);
        room.setStatus(status);
        roomRepository.save(room);
    }

    @Test
    void testFilterByParticipantCount_returnsRoomsSortedByCapacityAsc() {
        List<Room> result = roomService.filterByParticipantCount(10);

        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals("R-02", result.get(0).getRoomId());
        assertEquals(12, result.get(0).getCapacity());
        assertEquals("R-03", result.get(1).getRoomId());
        assertEquals(25, result.get(1).getCapacity());
        assertEquals("R-04", result.get(2).getRoomId());
        assertEquals(100, result.get(2).getCapacity());
    }

    @Test
    void testFilterByParticipantCount_invalidCount_throwsException() {
        RoomException exZero = assertThrows(RoomException.class, () -> roomService.filterByParticipantCount(0));
        assertEquals("Số người tham dự phải lớn hơn 0", exZero.getMessage());

        RoomException exNeg = assertThrows(RoomException.class, () -> roomService.filterByParticipantCount(-5));
        assertEquals("Số người tham dự phải lớn hơn 0", exNeg.getMessage());
    }

    @Test
    void testFilterRooms_byStatusOnly() {
        List<Room> availableRooms = roomService.filterRooms(null, "AVAILABLE");

        assertEquals(3, availableRooms.size());
        assertTrue(availableRooms.stream().allMatch(r -> "AVAILABLE".equalsIgnoreCase(r.getStatus())));

        List<Room> maintenanceRooms = roomService.filterRooms(null, "MAINTENANCE");
        assertEquals(1, maintenanceRooms.size());
        assertEquals("R-04", maintenanceRooms.get(0).getRoomId());
    }

    @Test
    void testFilterRooms_byCapacityAndStatus() {
        List<Room> result = roomService.filterRooms(10, "AVAILABLE");

        assertEquals(2, result.size());
        assertEquals("R-02", result.get(0).getRoomId());
        assertEquals(12, result.get(0).getCapacity());
        assertEquals("R-03", result.get(1).getRoomId());
        assertEquals(25, result.get(1).getCapacity());
    }

    @Test
    void testFilterRooms_withoutParams_returnsAll() {
        List<Room> all = roomService.filterRooms(null, null);
        assertEquals(4, all.size());
    }

    @Test
    void api_getAllRooms_returnsOk() {
        ResponseEntity<List<Room>> response = roomController.getRooms(null, null);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(4, response.getBody().size());
    }

    @Test
    void api_filterRoomsByParticipants_returnsOk() {
        ResponseEntity<List<Room>> response = roomController.getRooms(10, null);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(3, response.getBody().size());
        assertEquals(12, response.getBody().get(0).getCapacity());
        assertEquals(25, response.getBody().get(1).getCapacity());
        assertEquals(100, response.getBody().get(2).getCapacity());
    }

    @Test
    void api_filterRoomsByParticipantsAndStatus_returnsOk() {
        ResponseEntity<List<Room>> response = roomController.getRooms(10, "AVAILABLE");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
        assertEquals("R-02", response.getBody().get(0).getRoomId());
        assertEquals("R-03", response.getBody().get(1).getRoomId());
    }

    @Test
    void api_filterRoomsInvalidParticipants_throwsException() {
        RoomException ex = assertThrows(RoomException.class, () -> roomController.getRooms(0, null));
        assertEquals("Số người tham dự phải lớn hơn 0", ex.getMessage());
    }
}
