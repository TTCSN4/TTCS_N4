
package com.ttcs.meetingmanagement.equipment;

import com.ttcs.meetingmanagement.model.EquipmentAvailabilityStatus;
import com.ttcs.meetingmanagement.model.EquipmentBooking;
import com.ttcs.meetingmanagement.model.EquipmentBookingStatus;
import com.ttcs.meetingmanagement.model.Meeting;

import com.ttcs.meetingmanagement.repository.EquipmentBookingRepository;
import com.ttcs.meetingmanagement.repository.MeetingRepository;

import com.ttcs.meetingmanagement.dto.CreateEquipmentRequest;
import com.ttcs.meetingmanagement.dto.UpdateEquipmentRequest;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/equipment")
@CrossOrigin(origins = "*")
public class EquipmentController {

    private static final ZoneId VN_ZONE =
            ZoneId.of("Asia/Ho_Chi_Minh");

    private final EquipmentReservationService reservations;
    private final EquipmentService equipmentService;
    private final EquipmentRepository equipmentRepository;
    private final EquipmentBookingRepository bookingRepository;
    private final MeetingRepository meetingRepository;

    public EquipmentController(
            EquipmentService equipmentService,
            EquipmentRepository equipmentRepository,
            EquipmentBookingRepository bookingRepository,
            MeetingRepository meetingRepository, EquipmentReservationService reservations) {
        this.reservations=reservations;

        this.equipmentService = equipmentService;
        this.equipmentRepository = equipmentRepository;
        this.bookingRepository = bookingRepository;
        this.meetingRepository = meetingRepository;
    }

    // =========================================
    // 1. LAY DANH SACH THIET BI
    // =========================================

    @GetMapping
    public ResponseEntity<List<Equipment>> getAllEquipment() {

        return ResponseEntity.ok(
                equipmentService.getAllEquipment()
        );
    }

    // =========================================
    // 2. LAY CHI TIET THIET BI
    // =========================================

    @GetMapping("/{id}")
    public ResponseEntity<Equipment> getEquipmentById(
            @PathVariable String id) {

        return ResponseEntity.ok(
                equipmentService.getEquipmentById(id)
        );
    }

    // =========================================
    // 3. THEM THIET BI
    // =========================================

    @PostMapping
    public ResponseEntity<Equipment> createEquipment(
            @Valid @RequestBody CreateEquipmentRequest request) {

        Equipment equipment =
                equipmentService.createEquipment(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(equipment);
    }

    // =========================================
    // 4. CAP NHAT THIET BI
    // =========================================

    @PutMapping("/{id}")
    public ResponseEntity<Equipment> updateEquipment(
            @PathVariable String id,
            @Valid @RequestBody UpdateEquipmentRequest request) {

        return ResponseEntity.ok(
                equipmentService.updateEquipment(id, request)
        );
    }

    // =========================================
    // 5. XOA THIET BI
    // =========================================

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEquipment(
            @PathVariable String id) {

        equipmentService.deleteEquipment(id);

        return ResponseEntity.noContent().build();
    }

    // =========================================
    // 6. KIEM TRA THIET BI DA DUOC DAT
    // =========================================

    @GetMapping("/{id}/booked")
    public ResponseEntity<Boolean> checkBooked(
            @PathVariable String id) {

        return ResponseEntity.ok(
                equipmentService.isBooked(id)
        );
    }


    @GetMapping("/status") @Transactional(readOnly=true)
    public List<EquipmentStatusResponse> status(@RequestParam String room,@RequestParam OffsetDateTime at){
        return equipmentRepository.findByRoomIdIgnoreCaseOrderByEquipmentNameAsc(room.trim()).stream().map(e->{
            int total=e.getTotalQuantity()==null?0:e.getTotalQuantity();
            int available=reservations.available(e,at.atZoneSameInstant(VN_ZONE).toLocalDateTime(),at.plusNanos(1).atZoneSameInstant(VN_ZONE).toLocalDateTime(),java.util.Set.of());
            int maintenance="AVAILABLE".equalsIgnoreCase(e.getStatus())?0:total;
            return new EquipmentStatusResponse(e.getEquipmentId(),e.getEquipmentName(),e.getRoomId(),total,total-available-maintenance,maintenance,available,
                List.of(maintenance>0?EquipmentAvailabilityStatus.MAINTENANCE:available>0?EquipmentAvailabilityStatus.AVAILABLE:EquipmentAvailabilityStatus.BOOKED));
        }).toList();
    }
    @PostMapping("/bookings") @Transactional
    public BookingResponse book(@RequestBody EquipmentBookingRequest request){
        if(request==null||request.meetingId()==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Meeting ID is required");
        Meeting m=meetingRepository.findByIdForUpdate(request.meetingId()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Meeting not found"));
        String actor=com.ttcs.meetingmanagement.security.BackendActor.requireUserId();
        if(!actor.equals(m.organizerId))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Only the organizer can book equipment");
        if(!"SCHEDULED".equals(m.status)||!m.startTime.isAfter(java.time.LocalDateTime.now(VN_ZONE)))throw new ResponseStatusException(HttpStatus.CONFLICT,"Meeting cannot be modified");
        if(!java.util.Objects.equals(request.room(),m.roomId)||request.startTime()==null||request.endTime()==null
            ||!request.startTime().atZoneSameInstant(VN_ZONE).toLocalDateTime().equals(m.startTime)
            ||!request.endTime().atZoneSameInstant(VN_ZONE).toLocalDateTime().equals(m.endTime))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Reservation must match meeting room and time");
        var items=new ArrayList<>(reservations.items(m.meetingId));
        items.removeIf(i->i.equipmentId().equals(request.equipmentId()));
        items.add(new EquipmentReservationService.Item(request.equipmentId(),request.quantity()));
        reservations.replace(m,items);
        return new BookingResponse(m.meetingId+":"+request.equipmentId(),request.equipmentId(),m.meetingId,m.roomId,request.quantity(),request.startTime(),request.endTime(),EquipmentBookingStatus.BOOKED);
    }
    @DeleteMapping("/bookings/{meetingId}/{equipmentId}") @Transactional
    public ResponseEntity<Void> cancelBooking(@PathVariable String meetingId,@PathVariable String equipmentId){
        Meeting m=meetingRepository.findByIdForUpdate(meetingId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Meeting not found"));
        if(!com.ttcs.meetingmanagement.security.BackendActor.requireUserId().equals(m.organizerId))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Only the organizer can cancel equipment");
        var items=new ArrayList<>(reservations.items(meetingId));items.removeIf(i->i.equipmentId().equals(equipmentId));reservations.replace(m,items);
        return ResponseEntity.noContent().build();
    }
    public record EquipmentBookingRequest(String equipmentId,String meetingId,String room,Integer quantity,OffsetDateTime startTime,OffsetDateTime endTime){}
    public record BookingResponse(String id,String equipmentId,String meetingId,String room,int quantity,OffsetDateTime startTime,OffsetDateTime endTime,EquipmentBookingStatus status){}
    public record EquipmentStatusResponse(String equipmentId,String name,String room,int totalQuantity,int bookedQuantity,int maintenanceQuantity,int availableQuantity,List<EquipmentAvailabilityStatus> statuses){}
}
