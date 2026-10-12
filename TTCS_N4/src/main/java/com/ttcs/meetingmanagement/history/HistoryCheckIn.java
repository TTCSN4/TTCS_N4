package com.ttcs.meetingmanagement.history;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
@Entity @Table(name="check_in_log")
public class HistoryCheckIn {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @Column(name="meeting_id",nullable=false) public Long meetingId;
 @Column(name="user_id",nullable=false) public String userId;
 @Column(name="check_in_time") public OffsetDateTime checkInTime;
}
