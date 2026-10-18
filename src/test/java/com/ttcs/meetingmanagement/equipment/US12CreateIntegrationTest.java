package com.ttcs.meetingmanagement.equipment;
import com.ttcs.meetingmanagement.controller.MeetingController;
import com.ttcs.meetingmanagement.model.User;
import com.ttcs.meetingmanagement.room.Room;
import com.ttcs.meetingmanagement.room.RoomRepository;
import com.ttcs.meetingmanagement.repository.*;
import com.ttcs.meetingmanagement.rbac.AuthenticatedUserIdentity;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:us12create;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","spring.jpa.hibernate.ddl-auto=create-drop","app.reminder.enabled=false"})
class US12CreateIntegrationTest {
    @Autowired MeetingController controller;
    @Autowired EquipmentController equipmentController;
    @Autowired UserRepository users;
    @Autowired RoomRepository rooms;
    @Autowired EquipmentRepository equipment;
    @Autowired MeetingRepository meetings;
    @Autowired EquipmentBookingRepository bookings;
    User owner;Room room;Equipment device;
    @BeforeEach void setup(){
        owner=new User();owner.userId=UUID.randomUUID().toString();owner.fullName="Owner";owner.email=owner.userId+"@test.local";owner.status="ACTIVE";users.saveAndFlush(owner);
        room=new Room();room.roomId=UUID.randomUUID().toString();room.roomName="Test";room.capacity=10;room.status="AVAILABLE";rooms.saveAndFlush(room);
        device=new Equipment();device.setEquipmentId(UUID.randomUUID().toString());device.setRoomId(room.roomId);device.setEquipmentName("Projector");device.setType("TV");device.setTotalQuantity(3);device.setStatus("AVAILABLE");equipment.saveAndFlush(device);
        AuthenticatedUserIdentity identity=()->owner.userId;
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(identity,null,List.of()));
    }
    @AfterEach void cleanup(){SecurityContextHolder.clearContext();}
    MeetingController.MeetingRequest request(int quantity){var start=LocalDateTime.of(2099,1,1,9,0);return new MeetingController.MeetingRequest("Created","Notes",start,start.plusHours(1),owner.userId,List.of(),"WEEKLY",1,room.roomId,List.of(new EquipmentReservationService.Item(device.getEquipmentId(),quantity)));}
    @Test void createsRequestedQuantityForEveryOccurrence(){var first=controller.create(request(2));var created=meetings.findAll().stream().filter(m->owner.userId.equals(m.organizerId)).toList();assertEquals(2,created.size());for(var m:created){var rows=bookings.findAllByMeetingId(m.meetingId);assertEquals(1,rows.size());assertEquals(2,rows.get(0).getQuantity());assertEquals(first.recurrenceSeriesId,m.recurrenceSeriesId);}}
    @Test void insufficientEquipmentRollsBackNewMeetings(){assertEquals(409,assertThrows(ResponseStatusException.class,()->controller.create(request(4))).getStatusCode().value());assertTrue(meetings.findAll().stream().noneMatch(m->owner.userId.equals(m.organizerId)));assertTrue(bookings.findAllByEquipment_EquipmentId(device.getEquipmentId()).isEmpty());}
    @Test void compositeBookingIdPreservesExistingDeleteRoute(){var first=controller.create(request(2));equipmentController.cancelBookingId(first.meetingId+":"+device.getEquipmentId());assertTrue(bookings.findAllByMeetingId(first.meetingId).isEmpty());assertTrue(meetings.existsById(first.meetingId));}
}
