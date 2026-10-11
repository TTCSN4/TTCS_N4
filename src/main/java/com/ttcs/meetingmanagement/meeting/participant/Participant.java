package com.ttcs.meetingmanagement.meeting.participant;
import jakarta.persistence.*;
@Entity
@Table(name = "participants")
public class Participant {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private String name; private String role;
    protected Participant() {}
    public Participant(String name, String role) { this.name = name; this.role = role; }
    public Long getId() { return id; } public String getName() { return name; } public String getRole() { return role; }
}
