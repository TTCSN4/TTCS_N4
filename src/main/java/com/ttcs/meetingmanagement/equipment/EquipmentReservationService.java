package com.ttcs.meetingmanagement.equipment;
import com.ttcs.meetingmanagement.model.*;
import com.ttcs.meetingmanagement.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.*;

@Service @Transactional
public class EquipmentReservationService {
    public record Item(String equipmentId,Integer quantity) {}
    private record Event(LocalDateTime time,int delta) {}
    private final EquipmentRepository equipment;
    private final EquipmentBookingRepository bookings;
    public EquipmentReservationService(EquipmentRepository equipment,EquipmentBookingRepository bookings){this.equipment=equipment;this.bookings=bookings;}
    public List<Item> items(String meetingId){return bookings.findAllByMeetingId(meetingId).stream().map(b->new Item(b.getEquipment().getEquipmentId(),b.getQuantity())).toList();}
    public void release(String meetingId){bookings.deleteAll(bookings.findAllByMeetingId(meetingId));bookings.flush();}
    public void replace(Meeting meeting,List<Item> requested){
        if(requested==null)requested=items(meeting.meetingId);
        Map<String,Integer> quantities=new TreeMap<>();
        for(Item i:requested){
            if(i==null||i.equipmentId()==null||i.equipmentId().isBlank()||i.quantity()==null||i.quantity()<1)throw error(HttpStatus.BAD_REQUEST,"Equipment ID and positive quantity are required");
            if(quantities.putIfAbsent(i.equipmentId().trim(),i.quantity())!=null)throw error(HttpStatus.BAD_REQUEST,"Duplicate equipment ID");
        }
        List<EquipmentBooking> result=new ArrayList<>();
        // Consistent lock order prevents concurrent reservations exceeding stock.
        Set<String> locks=new TreeSet<>(quantities.keySet());
        items(meeting.meetingId).forEach(i->locks.add(i.equipmentId()));
        Map<String,Equipment> locked=new HashMap<>();
        for(String id:locks)locked.put(id,equipment.findByIdForUpdate(id).orElseThrow(()->error(HttpStatus.NOT_FOUND,"Equipment not found: "+id)));
        for(var entry:quantities.entrySet()){
            Equipment e=locked.get(entry.getKey());
            if(!Objects.equals(e.getRoomId(),meeting.roomId))throw error(HttpStatus.BAD_REQUEST,"Equipment does not belong to meeting room: "+entry.getKey());
            int remaining=remaining(e,meeting.startTime,meeting.endTime,Set.of(meeting.meetingId),bookings.lockForEquipment(e.getEquipmentId()));
            if(entry.getValue()>remaining)throw error(HttpStatus.CONFLICT,"Equipment "+entry.getKey()+": remaining quantity: "+remaining);
            EquipmentBooking b=new EquipmentBooking();b.setMeeting(meeting);b.setEquipment(e);b.setQuantity(entry.getValue());result.add(b);
        }
        release(meeting.meetingId);bookings.saveAll(result);bookings.flush();
    }
    @Transactional(readOnly=true)
    public int available(Equipment e,LocalDateTime start,LocalDateTime end,Set<String> ignored){
        return remaining(e,start,end,ignored,bookings.findAllByEquipment_EquipmentId(e.getEquipmentId()));
    }
    private int remaining(Equipment e,LocalDateTime start,LocalDateTime end,Set<String> ignored,List<EquipmentBooking> allocations){
        if(start==null||end==null||!end.isAfter(start))throw error(HttpStatus.BAD_REQUEST,"Invalid time range");
        if(!"AVAILABLE".equalsIgnoreCase(e.getStatus()))return 0;
        List<Event> events=new ArrayList<>();
        for(EquipmentBooking b:allocations){
            Meeting m=b.getMeeting();
            if(ignored.contains(m.getMeetingId())||"CANCELLED".equals(m.getStatus())||!m.getStartTime().isBefore(end)||!m.getEndTime().isAfter(start))continue;
            events.add(new Event(m.getStartTime().isBefore(start)?start:m.getStartTime(),b.getQuantity()));
            events.add(new Event(m.getEndTime().isAfter(end)?end:m.getEndTime(),-b.getQuantity()));
        }
        events.sort(Comparator.comparing(Event::time).thenComparingInt(Event::delta));
        int used=0,peak=0;for(Event event:events){used+=event.delta();peak=Math.max(peak,used);}
        return Math.max(0,(e.getTotalQuantity()==null?0:e.getTotalQuantity())-peak);
    }
    private static ResponseStatusException error(HttpStatus status,String message){return new ResponseStatusException(status,message);}
}
