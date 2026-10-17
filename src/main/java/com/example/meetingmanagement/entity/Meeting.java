package com.example.meetingmanagement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Entity
@Table(name = "meetings")
public class Meeting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "start_time", nullable = false)
    private OffsetDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private OffsetDateTime endTime;

    @Column(name = "organizer_id", nullable = false, length = 30)
    private String organizerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MeetingStatus status = MeetingStatus.SCHEDULED;

    @Column(name = "recurrence_series_id")
    private Long recurrenceSeriesId;

    @Column(length = 120) private String room;
    @Column(name="recurrence_rule",length=255) private String recurrenceRule;
    @ElementCollection @CollectionTable(name="meeting_participants",joinColumns=@JoinColumn(name="meeting_id"))
    @Column(name="user_id",length=50) private Set<String> participantIds=new LinkedHashSet<>();
    @ElementCollection @CollectionTable(name="meeting_equipment",joinColumns=@JoinColumn(name="meeting_id"))
    private List<MeetingEquipment> equipment=new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public OffsetDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(OffsetDateTime startTime) {
        this.startTime = startTime;
    }

    public OffsetDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(OffsetDateTime endTime) {
        this.endTime = endTime;
    }

    public String getOrganizerId() {
        return organizerId;
    }

    public void setOrganizerId(String organizerId) {
        this.organizerId = organizerId;
    }

    public MeetingStatus getStatus() {
        return status;
    }

    public void setStatus(MeetingStatus status) {
        this.status = status;
    }

    public Long getRecurrenceSeriesId() {
        return recurrenceSeriesId;
    }

    public void setRecurrenceSeriesId(Long recurrenceSeriesId) {
        this.recurrenceSeriesId = recurrenceSeriesId;
    }
    public String getRoom(){return room;} public void setRoom(String room){this.room=room;}
    public String getRecurrenceRule(){return recurrenceRule;} public void setRecurrenceRule(String value){recurrenceRule=value;}
    public Set<String> getParticipantIds(){return participantIds;} public void setParticipantIds(Collection<String> values){participantIds.clear();if(values!=null)participantIds.addAll(values);}
    public List<MeetingEquipment> getEquipment(){return equipment;} public void setEquipment(Collection<MeetingEquipment> values){equipment.clear();if(values!=null)equipment.addAll(values);}

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
