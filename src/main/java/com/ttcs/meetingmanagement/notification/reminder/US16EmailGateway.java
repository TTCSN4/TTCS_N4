package com.ttcs.meetingmanagement.notification.reminder;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.springframework.data.jpa.repository.*;
import java.time.*;
import java.util.*;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.ttcs.meetingmanagement.model.Meeting;

interface US16EmailGateway{void send(US16NotificationType type,Meeting meeting,String recipient);}
