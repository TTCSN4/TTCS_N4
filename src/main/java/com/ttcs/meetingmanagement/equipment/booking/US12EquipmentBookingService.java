package com.ttcs.meetingmanagement.equipment.booking;

import com.ttcs.meetingmanagement.equipment.*;
import com.ttcs.meetingmanagement.model.*;
import com.ttcs.meetingmanagement.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.*;
import java.util.*;

@Service
public class US12EquipmentBookingService {
    private static final ZoneId VN=ZoneId.of("Asia/Ho_Chi_Minh");
    private final MeetingRepository meetings;
    private final EquipmentRepository equipment;
    private final EquipmentBookingRepository reservations;
    private final US12MeetingEquipmentRepository lines;
    public US12EquipmentBookingService(MeetingRepository meetings,EquipmentRepository equipment,
            EquipmentBookingRepository reservations,US12MeetingEquipmentRepository lines) {
        this.meetings=meetings;this.equipment=equipment;this.reservations=reservations;this.lines=lines;
    }
    @Transactional
    public US12MeetingEquipment book(String meetingId,String actor,String equipmentId,int quantity) {
        Meeting meeting=lockMeeting(meetingId,actor);
        if(quantity<1)throw error(HttpStatus.BAD_REQUEST,"Quantity must be positive");
        Equipment item=equipment.findByIdForUpdate(equipmentId).orElseThrow(()->error(HttpStatus.NOT_FOUND,"Equipment not found"));
        if(!Objects.equals(item.getRoomId(),meeting.roomId))throw error(HttpStatus.BAD_REQUEST,"Equipment belongs to another room");
        if(Set.of("MAINTENANCE","INACTIVE","UNAVAILABLE").contains(item.getStatus().toUpperCase(Locale.ROOT)))
            throw error(HttpStatus.CONFLICT,"Equipment is unavailable");
        OffsetDateTime start=meeting.startTime.atZone(VN).toOffsetDateTime(),end=meeting.endTime.atZone(VN).toOffsetDateTime();
        List<Event> events=new ArrayList<>();
        var existing=reservations.findAllByEquipment_EquipmentId(equipmentId);
        for(var r:existing) {
            if(r.getStatus()==EquipmentBookingStatus.CANCELLED || Objects.equals(meetingId,r.getMeetingId())
                    || !r.getStartTime().isBefore(end) || !r.getEndTime().isAfter(start))continue;
            events.add(new Event(r.getStartTime().isBefore(start)?start:r.getStartTime(),r.getQuantity()));
            events.add(new Event(r.getEndTime().isAfter(end)?end:r.getEndTime(),-r.getQuantity()));
        }
        events.add(new Event(start,quantity));events.add(new Event(end,-quantity));
        events.sort(Comparator.comparing(Event::time).thenComparingInt(Event::delta));
        int used=0;for(var e:events){used+=e.delta();if(used>item.getTotalQuantity())throw error(HttpStatus.CONFLICT,"Not enough available equipment");}
        existing.stream().filter(r->Objects.equals(meetingId,r.getMeetingId())&&r.getStatus()!=EquipmentBookingStatus.CANCELLED)
            .forEach(r->r.setStatus(EquipmentBookingStatus.CANCELLED));
        reservations.saveAll(existing);
        EquipmentBooking r=new EquipmentBooking();r.setEquipment(item);r.setMeetingId(meetingId);r.setRoom(meeting.roomId);
        r.setQuantity(quantity);r.setStartTime(start);r.setEndTime(end);r.setStatus(EquipmentBookingStatus.BOOKED);reservations.save(r);
        return lines.save(new US12MeetingEquipment(meetingId,equipmentId,quantity));
    }
    @Transactional
    public void cancel(String meetingId,String actor,String equipmentId) {
        lockMeeting(meetingId,actor);
        equipment.findByIdForUpdate(equipmentId).orElseThrow(()->error(HttpStatus.NOT_FOUND,"Equipment not found"));
        var existing=reservations.findAllByMeetingId(meetingId);
        existing.stream().filter(r->equipmentId.equals(r.getEquipment().getEquipmentId()))
            .forEach(r->r.setStatus(EquipmentBookingStatus.CANCELLED));
        reservations.saveAll(existing);lines.deleteById(new US12MeetingEquipmentId(meetingId,equipmentId));
    }
    @Transactional(readOnly=true)
    public List<US12MeetingEquipment> list(String meetingId) {
        if(!meetings.existsById(meetingId))throw error(HttpStatus.NOT_FOUND,"Meeting not found");
        return lines.findById_MeetingId(meetingId);
    }
    private Meeting lockMeeting(String id,String actor) {
        Meeting m=meetings.findByIdForUpdate(id).orElseThrow(()->error(HttpStatus.NOT_FOUND,"Meeting not found"));
        if(!actor.equals(m.organizerId))throw error(HttpStatus.FORBIDDEN,"Only the organizer can book equipment");
        if(!Set.of("SCHEDULED","CONFIRMED").contains(m.status)||!m.startTime.isAfter(LocalDateTime.now()))
            throw error(HttpStatus.CONFLICT,"Meeting is not a future active meeting");
        if(m.roomId==null)throw error(HttpStatus.BAD_REQUEST,"Book a meeting room first");
        return m;
    }
    private record Event(OffsetDateTime time,int delta) {}
    private static ResponseStatusException error(HttpStatus status,String message){return new ResponseStatusException(status,message);}
}
