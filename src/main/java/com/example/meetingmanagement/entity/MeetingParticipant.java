package com.example.meetingmanagement.entity;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
@Entity @Table(name="meeting_participant") public class MeetingParticipant {
    @Id @Column(name="participant_id",length=255) private String id;
    @ManyToOne(optional=false) @JoinColumn(name="meeting_id") private Meeting meeting;
    @Column(name="user_id",length=255) private String userId;
    @Column(length=255) private String status="PENDING";
    @Column(name="responded_at") private OffsetDateTime respondedAt;
    protected MeetingParticipant(){} MeetingParticipant(Meeting meeting,String userId){id=UUID.randomUUID().toString();this.meeting=meeting;this.userId=userId;}
    public String getUserId(){return userId;}
}
