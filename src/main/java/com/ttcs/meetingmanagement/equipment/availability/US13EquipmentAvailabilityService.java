package com.ttcs.meetingmanagement.equipment.availability;

import com.ttcs.meetingmanagement.equipment.*;
import com.ttcs.meetingmanagement.model.*;
import com.ttcs.meetingmanagement.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.OffsetDateTime;
import java.util.*;

@Service @Transactional(readOnly=true)
public class US13EquipmentAvailabilityService {
    private final EquipmentRepository equipment;
    private final EquipmentBookingRepository reservations;
    private final MeetingRepository meetings;
    public US13EquipmentAvailabilityService(EquipmentRepository equipment,EquipmentBookingRepository reservations,MeetingRepository meetings){
        this.equipment=equipment;this.reservations=reservations;this.meetings=meetings;
    }
    public List<Availability> available(String room,OffsetDateTime start,OffsetDateTime end) {
        if(start==null||end==null||!end.isAfter(start))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid time range");
        var items=room==null||room.isBlank()?equipment.findAll():equipment.findByRoomIdIgnoreCaseOrderByEquipmentNameAsc(room.trim());
        return items.stream().map(e->calculate(e,start,end)).toList();
    }
    private Availability calculate(Equipment item,OffsetDateTime start,OffsetDateTime end){
        int total=item.getTotalQuantity();String state=Objects.toString(item.getStatus(),"AVAILABLE").toUpperCase(Locale.ROOT);
        List<Event> events=new ArrayList<>();
        for(var r:reservations.findAllByEquipment_EquipmentId(item.getEquipmentId())){
            if(r.getStatus()==EquipmentBookingStatus.CANCELLED || !r.getStartTime().isBefore(end)||!r.getEndTime().isAfter(start))continue;
            if(r.getMeetingId()!=null&&meetings.findById(r.getMeetingId()).map(m->"CANCELLED".equals(m.status)).orElse(false))continue;
            var first=r.getStartTime().isBefore(start)?start:r.getStartTime();
            var last=r.getEndTime().isAfter(end)?end:r.getEndTime();
            boolean maintenance=r.getStatus()==EquipmentBookingStatus.MAINTENANCE;
            events.add(new Event(first,r.getQuantity(),maintenance));events.add(new Event(last,-r.getQuantity(),maintenance));
        }
        events.sort(Comparator.comparing(Event::time).thenComparingInt(Event::delta));
        int used=0,booked=0,maintenance=0,peak=0,peakBooked=0,peakMaintenance=0;
        for(var e:events){used+=e.delta();if(e.maintenance())maintenance+=e.delta();else booked+=e.delta();
            peak=Math.max(peak,used);peakBooked=Math.max(peakBooked,booked);peakMaintenance=Math.max(peakMaintenance,maintenance);}
        boolean unavailable=Set.of("MAINTENANCE","INACTIVE","UNAVAILABLE").contains(state);
        int free=unavailable?0:Math.max(0,total-peak);
        List<String> statuses=new ArrayList<>();
        if(free>0)statuses.add("AVAILABLE");if(peakBooked>0)statuses.add("BOOKED");
        if(peakMaintenance>0||"MAINTENANCE".equals(state))statuses.add("MAINTENANCE");
        if("INACTIVE".equals(state)||"UNAVAILABLE".equals(state))statuses.add(state);
        return new Availability(item.getEquipmentId(),item.getEquipmentName(),item.getRoomId(),total,peakBooked,
            "MAINTENANCE".equals(state)?total:peakMaintenance,free,statuses);
    }
    private record Event(OffsetDateTime time,int delta,boolean maintenance){}
    public record Availability(String equipmentId,String name,String roomId,int totalQuantity,int peakBookedQuantity,
        int peakMaintenanceQuantity,int availableQuantity,List<String> statuses){}
}
