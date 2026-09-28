package com.ttcs.meetingmanagement.meeting;
import com.ttcs.meetingmanagement.meeting.participant.Participant;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
@Entity @Table(name = "meetings")
public class Meeting {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private String title; private LocalDateTime startTime; private LocalDateTime endTime;
    @ManyToMany @JoinTable(name = "meeting_participants", joinColumns = @JoinColumn(name = "meeting_id"), inverseJoinColumns = @JoinColumn(name = "participant_id"))
    private Set<Participant> participants = new HashSet<>();
    protected Meeting() {}
    public Meeting(String title, LocalDateTime startTime, LocalDateTime endTime) { this.title = title; this.startTime = startTime; this.endTime = endTime; }
    public void addParticipant(Participant participant) { participants.add(participant); }
    public LocalDateTime getStartTime() { return startTime; } public LocalDateTime getEndTime() { return endTime; }
}
