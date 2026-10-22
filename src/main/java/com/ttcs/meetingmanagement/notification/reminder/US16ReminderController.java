package com.ttcs.meetingmanagement.notification.reminder;
import com.ttcs.meetingmanagement.security.BackendActor;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.*;
@RestController @RequestMapping("/api")
class US16ReminderController {
    private final US16ReminderService service;
    US16ReminderController(US16ReminderService service){this.service=service;}
    @PostMapping("/meetings/{id}/reminders") @ResponseStatus(HttpStatus.CREATED)
    US16Notification schedule(@PathVariable String id,@Valid @RequestBody US16ReminderRequest request){
        return service.schedule(id,BackendActor.requireUserId(),request);
    }
    @GetMapping("/meetings/{id}/reminders") List<US16Notification> list(@PathVariable String id){
        return service.list(id,BackendActor.requireUserId());
    }
    @PostMapping("/reminders/dispatch") Map<String,Integer> dispatch(){return Map.of("sent",service.dispatchDue());}
}
