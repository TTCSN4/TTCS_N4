package com.ttcs.meetingmanagement.equipment.booking;
import com.ttcs.meetingmanagement.security.BackendActor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.List;

@RestController @RequestMapping("/api/meetings/{meetingId}/equipment")
public class US12EquipmentBookingController {
    private final US12EquipmentBookingService service;
    public US12EquipmentBookingController(US12EquipmentBookingService service){this.service=service;}
    @PostMapping public US12MeetingEquipment book(@PathVariable String meetingId,@Valid @RequestBody Request request){
        return service.book(meetingId,BackendActor.requireUserId(),request.equipmentId(),request.quantity());
    }
    @GetMapping public List<US12MeetingEquipment> list(@PathVariable String meetingId){return service.list(meetingId);}
    @DeleteMapping("/{equipmentId}") public ResponseEntity<Void> cancel(@PathVariable String meetingId,@PathVariable String equipmentId){
        service.cancel(meetingId,BackendActor.requireUserId(),equipmentId);return ResponseEntity.noContent().build();
    }
    public record Request(@NotBlank String equipmentId,@Min(1) int quantity){}
}
