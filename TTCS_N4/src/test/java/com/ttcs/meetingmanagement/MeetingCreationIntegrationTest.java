package com.ttcs.meetingmanagement;
import com.ttcs.meetingmanagement.service.MeetingService;
import com.ttcs.meetingmanagement.controller.MeetingController.MeetingRequest;
import com.ttcs.meetingmanagement.room.*;
import com.ttcs.meetingmanagement.model.*;
import com.ttcs.meetingmanagement.equipment.*;
import com.ttcs.meetingmanagement.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;
import java.time.OffsetDateTime;import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:us01integration","spring.jpa.hibernate.ddl-auto=create-drop"})
class MeetingCreationIntegrationTest {
 @Autowired MeetingService service;@Autowired MeetingRepository meetings;@Autowired RoomRepository rooms;@Autowired EquipmentRepository equipment;@Autowired EquipmentBookingRepository bookings;
 @BeforeEach void prepare(){bookings.deleteAll();meetings.deleteAll();equipment.deleteAll();rooms.deleteAll();Room r=new Room();r.setRoomId("R1");r.setRoomName("Room test");r.setCapacity(2);r.setStatus("AVAILABLE");rooms.saveAndFlush(r);Equipment e=new Equipment();e.setEquipmentId("E1");e.setRoomId("R1");e.setEquipmentName("TV");e.setType("TV");e.setStatus("AVAILABLE");e.setTotalQuantity(1);equipment.saveAndFlush(e);}
 MeetingRequest request(List<String> devices){return new MeetingRequest("Integration test","Description",OffsetDateTime.parse("2099-01-05T09:00:00+07:00"),OffsetDateTime.parse("2099-01-05T10:00:00+07:00"),1L,List.of("a@example.com"),"NONE",0,"R1","Notes",devices);}
 @Test void createsMeetingEquipmentAndDetectsConflict(){var m=service.create(request(List.of("E1")));assertNotNull(m.getId());assertEquals("Notes",meetings.findById(m.getId()).orElseThrow().getNotes());assertEquals(1,bookings.count());assertFalse(service.availability("R1",m.getStartTime(),m.getEndTime()).available());assertThrows(ResponseStatusException.class,()->service.create(request(List.of())));assertEquals(1,meetings.count());}
 @Test void equipmentFailureRollsBackEverything(){assertThrows(ResponseStatusException.class,()->service.create(request(List.of("E1","MISSING"))));assertEquals(0,meetings.count());assertEquals(0,bookings.count());}
 @Test void capacityValidationAndAdjacentTimes(){var r=request(List.of());var tooMany=new MeetingRequest(r.title(),r.description(),r.startTime(),r.endTime(),1L,List.of("a@example.com","b@example.com"),"NONE",0,"R1",null,List.of());assertThrows(ResponseStatusException.class,()->service.create(tooMany));service.create(r);assertTrue(service.availability("R1",r.endTime(),r.endTime().plusHours(1)).available());assertThrows(ResponseStatusException.class,()->service.availability("R1",r.endTime(),r.startTime()));}
}
