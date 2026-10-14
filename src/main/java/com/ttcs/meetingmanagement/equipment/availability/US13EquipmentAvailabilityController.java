package com.ttcs.meetingmanagement.equipment.availability;
import org.springframework.web.bind.annotation.*;
import java.time.OffsetDateTime;
import java.util.List;
@RestController @RequestMapping("/api/equipment")
public class US13EquipmentAvailabilityController {
    private final US13EquipmentAvailabilityService service;
    public US13EquipmentAvailabilityController(US13EquipmentAvailabilityService service){this.service=service;}
    @GetMapping("/availability") public List<US13EquipmentAvailabilityService.Availability> available(
        @RequestParam(required=false) String room,@RequestParam OffsetDateTime startTime,@RequestParam OffsetDateTime endTime){
        return service.available(room,startTime,endTime);
    }
}
