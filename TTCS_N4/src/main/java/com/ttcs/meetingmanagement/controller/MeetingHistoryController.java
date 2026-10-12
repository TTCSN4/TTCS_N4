package com.ttcs.meetingmanagement.controller;
import com.ttcs.meetingmanagement.history.MeetingHistoryService;
import com.ttcs.meetingmanagement.model.Meeting;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.ResponseEntity;
import java.util.*;
@RestController @RequestMapping("/api/users/{userId}/meeting-history")
public class MeetingHistoryController {
 private final MeetingHistoryService service;
 public MeetingHistoryController(MeetingHistoryService service){this.service=service;}
 @GetMapping public List<Meeting> list(@PathVariable String userId){return service.getHistory(userId);}
 @GetMapping("/{meetingId}") public Meeting detail(@PathVariable String userId,@PathVariable Long meetingId){return service.getHistoryDetail(userId,meetingId);}
 @ExceptionHandler(ResponseStatusException.class) public ResponseEntity<Map<String,String>> invalid(ResponseStatusException e){return ResponseEntity.status(e.getStatusCode()).body(Map.of("message",e.getReason()==null?"Invalid request":e.getReason()));}
}
