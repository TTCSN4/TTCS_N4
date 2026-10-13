package com.ttcs.meetingmanagement.chatbot;
import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import tools.jackson.databind.*;
import com.ttcs.meetingmanagement.model.*;
import com.ttcs.meetingmanagement.room.*;
import com.ttcs.meetingmanagement.repository.*;
@RestController @RequestMapping("/api/chatbot")
class US25ChatbotController {
    final US25ChatbotBookingService service;
    final US25ChatConversationService chat;
    US25ChatbotController(US25ChatbotBookingService service, US25ChatConversationService chat) {
        this.service=service;
        this.chat=chat;
    }
    @PostMapping("/message") US25ChatMessageResponse message(@Valid @RequestBody US25ChatMessageRequest request) {
        return chat.replyFor(com.ttcs.meetingmanagement.security.BackendActor.requireUserId(), request);
    }
    @PostMapping("/book-room") @ResponseStatus(HttpStatus.CREATED)
    US25ChatBookingResponse book(@Valid @RequestBody US25ChatBookingRequest request) {
        String actor=com.ttcs.meetingmanagement.security.BackendActor.requireUserId();
        if(!actor.equals(request.organizerId()))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Organizer must match authenticated user");
        return service.book(request);
    }
    @GetMapping("/rooms/available") List<US25AvailableRoom> available(@RequestParam LocalDateTime startTime,@RequestParam LocalDateTime endTime,@RequestParam @Min(1) int attendees) {
        return service.availableRooms(startTime,endTime,attendees);
    }
}
