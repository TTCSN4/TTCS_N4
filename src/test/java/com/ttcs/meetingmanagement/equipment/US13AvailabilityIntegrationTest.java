package com.ttcs.meetingmanagement.equipment;
import com.ttcs.meetingmanagement.model.Meeting;
import com.ttcs.meetingmanagement.model.EquipmentBooking;
import com.ttcs.meetingmanagement.model.EquipmentAvailabilityStatus;
import com.ttcs.meetingmanagement.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:us13availability;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","spring.jpa.hibernate.ddl-auto=create-drop","app.reminder.enabled=false"})
class US13AvailabilityIntegrationTest {
    @Autowired EquipmentRepository equipment;
    @Autowired EquipmentBookingRepository bookings;
    @Autowired MeetingRepository meetings;
    @Autowired EquipmentAvailabilityController ranges;
    @Autowired EquipmentController points;
    @Test @Transactional void rangeAndPointQueriesReflectDatabaseChanges(){
        String room=UUID.randomUUID().toString();Equipment e=new Equipment();e.setEquipmentId(UUID.randomUUID().toString());e.setRoomId(room);e.setEquipmentName("TV");e.setType("TV");e.setStatus("AVAILABLE");e.setTotalQuantity(10);equipment.saveAndFlush(e);
        var start=OffsetDateTime.parse("2099-01-01T09:00:00+07:00");
        Meeting first=allocation(e,start,4),second=allocation(e,start.plusHours(1),4);
        assertEquals(6,ranges.availability(room,start,start.plusHours(2),null).get(0).availableQuantity());
        var point=points.status(room,start.plusMinutes(30)).get(0);assertEquals(4,point.bookedQuantity());assertEquals(6,point.availableQuantity());assertEquals(List.of(EquipmentAvailabilityStatus.AVAILABLE,EquipmentAvailabilityStatus.BOOKED),point.statuses());
        second.startTime=first.startTime;second.endTime=first.endTime;meetings.saveAndFlush(second);
        assertEquals(2,ranges.availability(room,start,start.plusHours(2),null).get(0).availableQuantity());
        first.status="CANCELLED";meetings.saveAndFlush(first);assertEquals(6,ranges.availability(room,start,start.plusHours(2),null).get(0).availableQuantity());
        assertEquals(10,ranges.availability(room,start,start.plusHours(2),second.meetingId).get(0).availableQuantity());
        e.setStatus("MAINTENANCE");equipment.saveAndFlush(e);assertEquals(0,ranges.availability(room,start,start.plusHours(2),null).get(0).availableQuantity());
    }
    private Meeting allocation(Equipment e,OffsetDateTime start,int quantity){Meeting m=new Meeting();m.title="Allocation";m.organizerId="U1";m.roomId=e.getRoomId();m.status="SCHEDULED";m.startTime=start.toLocalDateTime();m.endTime=m.startTime.plusHours(1);meetings.saveAndFlush(m);EquipmentBooking b=new EquipmentBooking();b.setMeeting(m);b.setEquipment(e);b.setQuantity(quantity);bookings.saveAndFlush(b);return m;}
}
