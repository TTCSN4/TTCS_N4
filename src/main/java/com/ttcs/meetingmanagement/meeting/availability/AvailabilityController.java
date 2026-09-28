package com.ttcs.meetingmanagement.meeting.availability;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.List;
import static com.ttcs.meetingmanagement.meeting.availability.AvailabilityDtos.*;
@RestController @RequestMapping("/api")
public class AvailabilityController {
    private final AvailabilityService service;
    public AvailabilityController(AvailabilityService service) { this.service = service; }
    @GetMapping("/participants") public ParticipantList participants() { return new ParticipantList(service.participants()); }
    @PostMapping("/availability") public AvailabilityResponse availability(@Valid @RequestBody AvailabilityRequest request) { return service.find(request); }
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse invalidRequest(IllegalArgumentException exception) { return new ErrorResponse(exception.getMessage()); }
    public record ParticipantList(List<ParticipantResponse> participants) {}
    public record ErrorResponse(String message) {}
}
