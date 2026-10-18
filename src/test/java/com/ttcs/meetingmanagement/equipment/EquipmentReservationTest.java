package com.ttcs.meetingmanagement.equipment;
import com.ttcs.meetingmanagement.model.Meeting;
import com.ttcs.meetingmanagement.model.EquipmentBooking;
import com.ttcs.meetingmanagement.repository.EquipmentBookingRepository;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EquipmentReservationTest {
    EquipmentRepository equipment=mock(EquipmentRepository.class);
    EquipmentBookingRepository bookings=mock(EquipmentBookingRepository.class);
    EquipmentReservationService service=new EquipmentReservationService(equipment,bookings);
    Equipment e;LocalDateTime start=LocalDateTime.of(2099,1,1,9,0);
    @BeforeEach void setup(){e=new Equipment();e.setEquipmentId("E1");e.setRoomId("R1");e.setTotalQuantity(10);e.setStatus("AVAILABLE");when(equipment.findByIdForUpdate("E1")).thenReturn(Optional.of(e));}
    EquipmentBooking booking(String id,int quantity,int from,int to){Meeting m=new Meeting();m.meetingId=id;m.roomId="R1";m.status="SCHEDULED";m.startTime=start.plusHours(from);m.endTime=start.plusHours(to);EquipmentBooking b=new EquipmentBooking();b.setMeeting(m);b.setEquipment(e);b.setQuantity(quantity);return b;}
    @Test void disjointBookingsUsePeakNotSum(){when(bookings.findAllByEquipment_EquipmentId("E1")).thenReturn(List.of(booking("M1",4,0,1),booking("M2",4,1,2)));assertEquals(6,service.available(e,start,start.plusHours(2),Set.of()));}
    @Test void overlappingBookingsAccumulate(){when(bookings.findAllByEquipment_EquipmentId("E1")).thenReturn(List.of(booking("M1",4,0,2),booking("M2",3,1,3)));assertEquals(3,service.available(e,start,start.plusHours(3),Set.of()));}
    @Test void endBoundaryIsAvailable(){when(bookings.findAllByEquipment_EquipmentId("E1")).thenReturn(List.of(booking("M1",10,0,1)));assertEquals(10,service.available(e,start.plusHours(1),start.plusHours(2),Set.of()));}
    @Test void cancellationAndEditPreviewDoNotConsumeStock(){var b=booking("M1",10,0,1);b.getMeeting().status="CANCELLED";when(bookings.findAllByEquipment_EquipmentId("E1")).thenReturn(List.of(b));assertEquals(10,service.available(e,start,start.plusHours(1),Set.of()));b.getMeeting().status="SCHEDULED";assertEquals(10,service.available(e,start,start.plusHours(1),Set.of("M1")));}
    @Test void maintenanceIsUnavailable(){e.setStatus("MAINTENANCE");assertEquals(0,service.available(e,start,start.plusHours(1),Set.of()));}
    @Test void insufficientStockReportsActualRemainingWithoutDeletingOldRows(){
        var m=booking("EDIT",1,0,2).getMeeting();when(bookings.lockForEquipment("E1")).thenReturn(List.of(booking("M1",4,0,1),booking("M2",4,1,2)));
        var ex=assertThrows(ResponseStatusException.class,()->service.replace(m,List.of(new EquipmentReservationService.Item("E1",7))));
        assertEquals(409,ex.getStatusCode().value());assertTrue(ex.getReason().contains("remaining quantity: 6"));verify(bookings,never()).deleteAll(any());
    }
    @Test void rejectsZeroAndDuplicateQuantities(){var m=booking("M",1,0,1).getMeeting();assertThrows(ResponseStatusException.class,()->service.replace(m,List.of(new EquipmentReservationService.Item("E1",0))));assertThrows(ResponseStatusException.class,()->service.replace(m,List.of(new EquipmentReservationService.Item("E1",1),new EquipmentReservationService.Item("E1",2))));}
}
