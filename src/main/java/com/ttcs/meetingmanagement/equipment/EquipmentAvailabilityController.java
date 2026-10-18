package com.ttcs.meetingmanagement.equipment;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** US13: minimum stock available throughout a half-open [start,end) interval. */
@RestController
@RequestMapping("/api/equipment")
public class EquipmentAvailabilityController {
    private final EquipmentRepository equipment;
    private final EquipmentReservationService reservations;
    public EquipmentAvailabilityController(EquipmentRepository equipment, EquipmentReservationService reservations) {
        this.equipment=equipment; this.reservations=reservations;
    }
    public record Availability(String equipmentId,String name,int totalQuantity,int availableQuantity,String status) {}
    @GetMapping("/availability")
    @Transactional(readOnly=true)
    public List<Availability> availability(@RequestParam String room,
            @RequestParam OffsetDateTime startTime,@RequestParam OffsetDateTime endTime,
            @RequestParam(required=false) String excludeMeetingId) {
        if(room.isBlank()||!endTime.isAfter(startTime))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Room and valid time range are required");
        var start=startTime.atZoneSameInstant(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDateTime();
        var end=endTime.atZoneSameInstant(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDateTime();
        Set<String> ignored=excludeMeetingId==null?Set.of():Set.of(excludeMeetingId);
        return equipment.findByRoomIdIgnoreCaseOrderByEquipmentNameAsc(room.trim()).stream()
            .map(e->new Availability(e.getEquipmentId(),e.getEquipmentName(),e.getTotalQuantity()==null?0:e.getTotalQuantity(),
                reservations.available(e,start,end,ignored),e.getStatus())).toList();
    }
}
