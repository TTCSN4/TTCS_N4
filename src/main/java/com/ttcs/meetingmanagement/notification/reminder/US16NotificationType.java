package com.ttcs.meetingmanagement.notification.reminder;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.springframework.data.jpa.repository.*;
import java.time.*;
import java.util.*;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.ttcs.meetingmanagement.model.Meeting;

enum US16NotificationType { MEETING_INVITATION, MEETING_REMINDER_15_MINUTES, MEETING_UPDATED, MEETING_CANCELLED }
