package com.ttcs.meetingmanagement.recurrence;
import jakarta.persistence.*;
@Entity @Table(name="meeting_recurrence")
public class MeetingRecurrence {
 @Id @Column(length=50) public String id;
 @Column(length=4000,nullable=false) public String configuration;
 public int occurrences;
}
