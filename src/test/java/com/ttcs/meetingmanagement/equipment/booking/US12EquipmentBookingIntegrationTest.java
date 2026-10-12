package com.ttcs.meetingmanagement.equipment.booking;
import com.ttcs.meetingmanagement.equipment.*;
import com.ttcs.meetingmanagement.model.*;
import com.ttcs.meetingmanagement.repository.*;
import com.ttcs.meetingmanagement.room.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest @Transactional
class US12EquipmentBookingIntegrationTest {
    @Autowired US12EquipmentBookingService service;@Autowired MeetingRepository meetings;
    @Autowired EquipmentRepository equipment;@Autowired EquipmentBookingRepository reservations;@Autowired RoomRepository rooms;
    @BeforeEach void fixture(){
        var room=new Room();room.roomId="US12-R";room.roomName="US12 room";room.capacity=10;room.status="AVAILABLE";rooms.save(room);
        var item=new Equipment();item.setEquipmentId("US12-E");item.setEquipmentName("Projector");item.setType("PROJECTOR");
        item.setRoomId(room.roomId);item.setTotalQuantity(2);item.setStatus("AVAILABLE");equipment.saveAndFlush(item);
        meeting("US12-M1");meeting("US12-M2");meetings.flush();
    }
    void meeting(String id){var m=new Meeting();m.meetingId=id;m.organizerId="US12-U";m.title=id;m.roomId="US12-R";
        m.startTime=LocalDateTime.of(2099,1,1,9,0);m.endTime=m.startTime.plusHours(1);m.status="SCHEDULED";meetings.save(m);}
    @Test void reservesUsingCompositeKeyAndReleases(){
        var line=service.book("US12-M1","US12-U","US12-E",1);reservations.flush();
        assertEquals("US12-M1",line.id.meetingId);assertEquals(1,service.list("US12-M1").size());
        service.cancel("US12-M1","US12-U","US12-E");reservations.flush();
        assertTrue(service.list("US12-M1").isEmpty());
        assertTrue(reservations.findAllByMeetingId("US12-M1").stream().allMatch(r->r.getStatus()==EquipmentBookingStatus.CANCELLED));
    }
    @Test void rejectsOverlappingQuantityBeyondCapacity(){
        service.book("US12-M1","US12-U","US12-E",1);reservations.flush();
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.book("US12-M2","US12-U","US12-E",2)).getStatusCode().value());
    }
    @Test void rejectsAnotherOrganizer(){assertEquals(403,assertThrows(ResponseStatusException.class,()->service.book("US12-M1","OTHER","US12-E",1)).getStatusCode().value());}
}
