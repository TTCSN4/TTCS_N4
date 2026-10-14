package com.ttcs.meetingmanagement.equipment.availability;
import com.ttcs.meetingmanagement.equipment.*;
import com.ttcs.meetingmanagement.model.*;
import com.ttcs.meetingmanagement.repository.*;
import org.junit.jupiter.api.Test;
import java.time.OffsetDateTime;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class US13EquipmentAvailabilityServiceTest {
    EquipmentRepository equipment=mock(EquipmentRepository.class);EquipmentBookingRepository reservations=mock(EquipmentBookingRepository.class);
    US13EquipmentAvailabilityService service=new US13EquipmentAvailabilityService(equipment,reservations,mock(MeetingRepository.class));
    OffsetDateTime start=OffsetDateTime.parse("2099-01-01T09:00:00+07:00");
    Equipment item(){var e=new Equipment();e.setEquipmentId("E");e.setEquipmentName("Projector");e.setTotalQuantity(5);e.setStatus("AVAILABLE");when(equipment.findAll()).thenReturn(List.of(e));return e;}
    EquipmentBooking booking(int startHour,int endHour,EquipmentBookingStatus status){var r=new EquipmentBooking();r.setQuantity(2);r.setStartTime(start.plusHours(startHour));r.setEndTime(start.plusHours(endHour));r.setStatus(status);return r;}
    @Test void usesPeakInsteadOfSumForBackToBackReservations(){
        item();when(reservations.findAllByEquipment_EquipmentId("E")).thenReturn(List.of(booking(0,1,EquipmentBookingStatus.BOOKED),booking(1,2,EquipmentBookingStatus.BOOKED)));
        assertEquals(3,service.available(null,start,start.plusHours(2)).getFirst().availableQuantity());
    }
    @Test void includesOverlappingMaintenanceButIgnoresCancellation(){
        item();when(reservations.findAllByEquipment_EquipmentId("E")).thenReturn(List.of(booking(0,2,EquipmentBookingStatus.BOOKED),booking(1,2,EquipmentBookingStatus.MAINTENANCE),booking(0,2,EquipmentBookingStatus.CANCELLED)));
        var result=service.available(null,start,start.plusHours(2)).getFirst();assertEquals(1,result.availableQuantity());assertTrue(result.statuses().contains("MAINTENANCE"));
    }
    @Test void staticMaintenanceMakesAllItemsUnavailable(){
        item().setStatus("MAINTENANCE");assertEquals(0,service.available(null,start,start.plusHours(2)).getFirst().availableQuantity());
    }
}
