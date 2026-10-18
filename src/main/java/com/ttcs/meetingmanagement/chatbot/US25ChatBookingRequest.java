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
record US25ChatBookingRequest(@NotBlank String organizerId,@NotBlank String title,String description,@NotNull LocalDateTime startTime,@NotNull LocalDateTime endTime,@Min(1)int attendees,String roomId,List<String> participantIds,Boolean recurring,String recurrenceRule) {
    US25ChatBookingRequest(String organizerId,String title,LocalDateTime startTime,LocalDateTime endTime,int attendees,String roomId) {
        this(organizerId,title,null,startTime,endTime,attendees,roomId,List.of(),false,null);
    }
}
