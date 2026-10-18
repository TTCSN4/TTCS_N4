package com.ttcs.meetingmanagement.roombooking;

import com.ttcs.meetingmanagement.MeetingmanagementApplication;
import com.ttcs.meetingmanagement.controller.MeetingController;
import com.ttcs.meetingmanagement.equipment.*;
import com.ttcs.meetingmanagement.model.*;
import com.ttcs.meetingmanagement.repository.*;
import com.ttcs.meetingmanagement.room.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.mail.javamail.JavaMailSender;
import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@SpringBootTest(classes = US09CancellationHttpIntegrationTest.TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:us09_http;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.mobile.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "app.mobile.jwt.issuer=us09-test-issuer", "app.mobile.jwt.audience=us09-test-audience",
        "app.reminder.enabled=false"
})
@Import(US09CancellationHttpIntegrationTest.TestMailConfiguration.class)
class US09CancellationHttpIntegrationTest {
    static final String OWNER="US09-OWNER", OTHER="US09-OTHER", ATTENDEE="US09-ATTENDEE", ROOM="US09-ROOM", EQUIPMENT="US09-EQUIPMENT";
    static final List<String> MEETING_IDS=List.of("US09-HTTP-401", "US09-HTTP-CANCEL", "US09-HTTP-REBOOK", "US09-HTTP-MEETING", "US09-HTTP-MEETING-REBOOK", "US09-HTTP-OTHER");
    @LocalServerPort int port;
    @Autowired MeetingRepository meetings;
    @Autowired RoomRepository rooms;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired EquipmentRepository equipment;
    @Autowired EquipmentBookingRepository equipmentBookings;
    final HttpClient client=HttpClient.newHttpClient();

    @BeforeEach void fixture() {
        cleanupFixture();
        Role booker=role("US09-BOOKER", "Người đặt lịch"), attendee=role("US09-ATTENDEE-ROLE", "Người tham dự");
        roles.save(booker); roles.save(attendee);
        users.save(user(OWNER,booker)); users.save(user(OTHER,booker)); users.save(user(ATTENDEE,attendee));
        Room room=new Room(); room.roomId=ROOM; room.roomName="US09 integration room"; room.capacity=8; room.status="AVAILABLE"; rooms.saveAndFlush(room);
        Equipment item=new Equipment(); item.setEquipmentId(EQUIPMENT); item.setRoomId(ROOM); item.setEquipmentName("US09 projector"); item.setType("PROJECTOR"); item.setTotalQuantity(1); item.setStatus("AVAILABLE"); equipment.saveAndFlush(item);
    }

    @AfterEach void cleanup() { cleanupFixture(); }

    @Test void cancellationEndpointsRequireJwtAndBookingRole() throws Exception {
        Meeting meeting=meeting("US09-HTTP-401",OWNER,ROOM); meetings.saveAndFlush(meeting);
        assertEquals(401, call("DELETE","/api/room-bookings/"+meeting.meetingId,null,null).statusCode());
        assertEquals(403, call("DELETE","/api/room-bookings/"+meeting.meetingId,token(ATTENDEE),null).statusCode());
        assertEquals(403, call("DELETE","/api/room-bookings/"+meeting.meetingId,token(OTHER),null).statusCode());
        assertEquals(ROOM,meetings.findById(meeting.meetingId).orElseThrow().roomId);
    }

    @Test void ownerCancelsRoomWithoutCsrfReleasesOnlyItsEquipmentThenBooksItAgainThroughUs08() throws Exception {
        Meeting cancelled=meeting("US09-HTTP-CANCEL",OWNER,ROOM), replacement=meeting("US09-HTTP-REBOOK",OWNER,null), other=meeting("US09-HTTP-OTHER",OTHER,ROOM);
        other.startTime=other.startTime.plusHours(2); other.endTime=other.endTime.plusHours(2);
        meetings.saveAndFlush(cancelled); meetings.saveAndFlush(replacement); meetings.saveAndFlush(other);
        EquipmentBooking cancelledBooking=booking(cancelled), otherBooking=booking(other);
        equipmentBookings.saveAndFlush(cancelledBooking); equipmentBookings.saveAndFlush(otherBooking);
        assertEquals(200,call("DELETE","/api/room-bookings/"+cancelled.meetingId,token(OWNER),null).statusCode());
        assertNull(meetings.findById(cancelled.meetingId).orElseThrow().roomId);
        assertEquals(EquipmentBookingStatus.CANCELLED,equipmentBookings.findById(cancelledBooking.getId()).orElseThrow().getStatus());
        assertEquals(EquipmentBookingStatus.BOOKED,equipmentBookings.findById(otherBooking.getId()).orElseThrow().getStatus());
        assertEquals(ROOM,meetings.findById(other.meetingId).orElseThrow().roomId);
        assertEquals(0,meetings.countRoomConflicts(ROOM,replacement.meetingId,replacement.startTime,replacement.endTime));
        assertEquals(200,call("POST","/api/room-bookings",token(OWNER),"{\"meetingId\":\""+replacement.meetingId+"\",\"roomId\":\""+ROOM+"\"}").statusCode());
        assertEquals(ROOM,meetings.findById(replacement.meetingId).orElseThrow().roomId);
    }

    @Test void ownerCancelsMeetingAndItsRoomNoLongerBlocksAvailability() throws Exception {
        Meeting cancelled=meeting("US09-HTTP-MEETING",OWNER,ROOM), replacement=meeting("US09-HTTP-MEETING-REBOOK",OWNER,null);
        meetings.saveAndFlush(cancelled); meetings.saveAndFlush(replacement);
        EquipmentBooking cancelledBooking=booking(cancelled); equipmentBookings.saveAndFlush(cancelledBooking);
        assertEquals(204,call("DELETE","/api/meetings/"+cancelled.meetingId+"?reason=Changed%20plans",token(OWNER),null).statusCode());
        Meeting saved=meetings.findById(cancelled.meetingId).orElseThrow();
        assertEquals("CANCELLED",saved.status); assertEquals(ROOM,saved.roomId);
        assertEquals(EquipmentBookingStatus.CANCELLED,equipmentBookings.findById(cancelledBooking.getId()).orElseThrow().getStatus());
        assertEquals(0,meetings.countRoomConflicts(ROOM,replacement.meetingId,replacement.startTime,replacement.endTime));
    }

    private void cleanupFixture() {
        MEETING_IDS.forEach(id -> equipmentBookings.deleteAll(equipmentBookings.findAllByMeetingId(id)));
        meetings.deleteAllById(MEETING_IDS);
        equipment.deleteById(EQUIPMENT);
        users.deleteAllById(List.of(OWNER,OTHER,ATTENDEE));
        roles.deleteAllById(List.of("US09-BOOKER","US09-ATTENDEE-ROLE"));
        rooms.deleteById(ROOM);
    }

    HttpResponse<String> call(String method,String path,String bearer,String body) throws Exception {
        HttpRequest.Builder request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).header("Accept","application/json");
        if(bearer!=null) request.header("Authorization","Bearer "+bearer);
        if(body!=null) request.header("Content-Type","application/json");
        return client.send(request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    static Role role(String id,String name){Role r=new Role();r.roleId=id;r.roleName=name;return r;}
    static User user(String id,Role role){User u=new User();u.userId=id;u.email=id.toLowerCase()+"@example.com";u.status="ACTIVE";u.role=role;return u;}
    static Meeting meeting(String id,String owner,String room){Meeting m=new Meeting();m.meetingId=id;m.organizerId=owner;m.roomId=room;m.title=id;m.status="SCHEDULED";m.startTime=LocalDateTime.now().plusDays(2).withMinute(0).withSecond(0).withNano(0);m.endTime=m.startTime.plusHours(1);return m;}
    private EquipmentBooking booking(Meeting meeting) { EquipmentBooking value=new EquipmentBooking(); value.setEquipment(equipment.findById(EQUIPMENT).orElseThrow()); value.setMeetingId(meeting.meetingId); value.setRoom(ROOM); value.setQuantity(1); value.setStartTime(meeting.startTime.atOffset(ZoneOffset.UTC)); value.setEndTime(meeting.endTime.atOffset(ZoneOffset.UTC)); value.setStatus(EquipmentBookingStatus.BOOKED); return value; }
    static String token(String subject) throws Exception {
        String header=b64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}"); long now=Instant.now().getEpochSecond();
        String payload=b64("{\"sub\":\""+subject+"\",\"iss\":\"us09-test-issuer\",\"aud\":[\"us09-test-audience\"],\"iat\":"+now+",\"exp\":"+(now+300)+"}");
        Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(Base64.getDecoder().decode("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="),"HmacSHA256"));
        return header+"."+payload+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal((header+"."+payload).getBytes(StandardCharsets.US_ASCII)));
    }
    static String b64(String value){return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));}

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @AutoConfigurationPackage(basePackages = "com.ttcs.meetingmanagement")
    @EnableJpaRepositories(basePackages = "com.ttcs.meetingmanagement")
    @ComponentScan(basePackages = "com.ttcs.meetingmanagement", excludeFilters = {
            @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {MeetingmanagementApplication.class, MeetingController.class}),
            @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.ttcs\\.meetingmanagement\\.chatbot\\..*")
    })
    static class TestApplication { }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestMailConfiguration {
        @Bean JavaMailSender javaMailSender() { return mock(JavaMailSender.class); }
    }
}
