package com.ttcs.meetingmanagement.recurrence;
import jakarta.persistence.*;
@Entity @Table(name="meeting_recurrence")
public class MeetingRecurrence {
 @Id public Long id;
 @Column(length=4000,nullable=false) public String configuration;
 public int occurrences;
}
