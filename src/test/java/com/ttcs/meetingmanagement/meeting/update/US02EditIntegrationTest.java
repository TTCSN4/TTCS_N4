package com.ttcs.meetingmanagement.meeting.update;
import com.ttcs.meetingmanagement.model.Meeting;
import com.ttcs.meetingmanagement.model.User;
import com.ttcs.meetingmanagement.model.MeetingParticipant;
import com.ttcs.meetingmanagement.room.Room;
import com.ttcs.meetingmanagement.room.RoomRepository;
import com.ttcs.meetingmanagement.repository.*;
import com.ttcs.meetingmanagement.equipment.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:us02edit;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","spring.jpa.hibernate.ddl-auto=create-drop","app.reminder.enabled=false"})
class US02EditIntegrationTest {
    @Autowired US02MeetingService service;
    @Autowired MeetingRepository meetings;
    @Autowired UserRepository users;
    @Autowired RoomRepository rooms;
    @Autowired EquipmentRepository equipment;
    @Autowired EquipmentBookingRepository bookings;
    @Autowired MeetingParticipantRepository participants;
    @Autowired EquipmentReservationService reservations;
    @Autowired PlatformTransactionManager transactions;
    Meeting meeting;User owner;Equipment device;String participantId;LocalDateTime start=LocalDateTime.of(2099,1,1,9,0);
    @BeforeEach void setup(){new TransactionTemplate(transactions).execute(status->{
        owner=new User();owner.userId=UUID.randomUUID().toString();owner.fullName="Owner";owner.email=owner.userId+"@test.local";owner.status="ACTIVE";users.saveAndFlush(owner);
        User guest=new User();guest.userId=UUID.randomUUID().toString();guest.fullName="Guest";guest.email=guest.userId+"@test.local";guest.status="ACTIVE";users.saveAndFlush(guest);participantId=guest.userId;
        Room room=new Room();room.roomId=UUID.randomUUID().toString();room.roomName="Test";room.capacity=10;room.status="AVAILABLE";rooms.saveAndFlush(room);
        device=new Equipment();device.setEquipmentId(UUID.randomUUID().toString());device.setRoomId(room.roomId);device.setEquipmentName("Projector");device.setType("TV");device.setTotalQuantity(3);device.setStatus("AVAILABLE");equipment.saveAndFlush(device);
        meeting=new Meeting();meeting.organizerId=owner.userId;meeting.roomId=room.roomId;meeting.title="Original";meeting.status="SCHEDULED";meeting.startTime=start;meeting.endTime=start.plusHours(1);meetings.saveAndFlush(meeting);
        reservations.replace(meeting,List.of(new EquipmentReservationService.Item(device.getEquipmentId(),2)));return null;});}
    US02MeetingRequest request(int quantity){return new US02MeetingRequest("Updated","Note",start.plusHours(2),start.plusHours(3),meeting.roomId,List.of(participantId),List.of(new EquipmentReservationService.Item(device.getEquipmentId(),quantity)),null,US02MeetingRequest.Scope.INSTANCE);}
    @Test void failedEquipmentChangeRollsBackMeetingParticipantsAndOldQuantity(){
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.update(meeting.meetingId,owner.userId,request(4))).getStatusCode().value());
        var saved=meetings.findById(meeting.meetingId).orElseThrow();assertEquals("Original",saved.title);assertEquals(start,saved.startTime);
        assertEquals(2,bookings.findAllByMeetingId(meeting.meetingId).get(0).getQuantity());assertTrue(participants.findByMeetingId(meeting.meetingId).isEmpty());
    }
    @Test void moveAndCancelUpdateAvailabilityWithoutDuplicatedBookingTimes(){
        service.update(meeting.meetingId,owner.userId,request(3));
        assertEquals(3,reservations.available(device,start,start.plusHours(1),Set.of()));assertEquals(0,reservations.available(device,start.plusHours(2),start.plusHours(3),Set.of()));
        assertEquals(participantId,participants.findByMeetingId(meeting.meetingId).get(0).userId);
        service.cancel(meeting.meetingId,owner.userId,"Cancelled");assertEquals("CANCELLED",meetings.findById(meeting.meetingId).orElseThrow().status);
        assertEquals(3,reservations.available(device,start.plusHours(2),start.plusHours(3),Set.of()));assertTrue(bookings.findAllByMeetingId(meeting.meetingId).isEmpty());
    }
    @Test void roomConflictPreservesOriginalData(){
        Meeting other=new Meeting();other.organizerId=owner.userId;other.roomId=meeting.roomId;other.title="Other";other.status="SCHEDULED";other.startTime=start.plusHours(2);other.endTime=start.plusHours(3);meetings.saveAndFlush(other);
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.update(meeting.meetingId,owner.userId,request(2))).getStatusCode().value());assertEquals(start,meetings.findById(meeting.meetingId).orElseThrow().startTime);
    }
}
