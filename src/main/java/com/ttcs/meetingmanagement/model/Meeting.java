package com.ttcs.meetingmanagement.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.OneToMany;
import jakarta.persistence.CascadeType;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "meeting")
public class Meeting {
    @Id
    @Column(name="meeting_id",length=255)
    private String id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name="room_id",length = 255)
    private String room;

    @Column(name = "start_time", nullable = false)
    private OffsetDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private OffsetDateTime endTime;

    @Column(name = "organizer_id", nullable = false)
    private String organizerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MeetingStatus status;

    @Column(name = "recurrence_series_id")
    private String recurrenceSeriesId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy="meeting",cascade=CascadeType.ALL,orphanRemoval=true,fetch=jakarta.persistence.FetchType.EAGER)
    private List<MeetingParticipant> participantEntities=new ArrayList<>();

    public Meeting() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id=id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getRoom() { return room; }
    public void setRoom(String room) { this.room = room; }
    public OffsetDateTime getStartTime() { return startTime; }
    public void setStartTime(OffsetDateTime startTime) { this.startTime = startTime; }
    public OffsetDateTime getEndTime() { return endTime; }
    public void setEndTime(OffsetDateTime endTime) { this.endTime = endTime; }
    public String getOrganizerId() { return organizerId; }
    public void setOrganizerId(String organizerId) { this.organizerId = organizerId; }
    public MeetingStatus getStatus() { return status; }
    public void setStatus(MeetingStatus status) { this.status = status; }
    public String getRecurrenceSeriesId() { return recurrenceSeriesId; }
    public void setRecurrenceSeriesId(String recurrenceSeriesId) { this.recurrenceSeriesId = recurrenceSeriesId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public List<String> getParticipants() { return participantEntities.stream().map(MeetingParticipant::getUserId).toList(); }
    public void setParticipants(List<String> participants) { participantEntities.clear();if(participants!=null)participants.forEach(v->participantEntities.add(new MeetingParticipant(this,v))); }
}
