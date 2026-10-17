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
import jakarta.persistence.OneToMany;
import jakarta.persistence.CascadeType;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Entity
@Table(name = "meeting")
public class Meeting {

    @Id
    @Column(name="meeting_id",length=255)
    private String id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "start_time", nullable = false)
    private OffsetDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private OffsetDateTime endTime;

    @Column(name = "organizer_id", nullable = false, length = 255)
    private String organizerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MeetingStatus status = MeetingStatus.SCHEDULED;

    @Column(name = "recurrence_series_id")
    private String recurrenceSeriesId;

    @Column(name="room_id",length = 255) private String room;
    @Column(name="is_recurring") private Boolean recurring=false;
    @Column(name="cancel_reason",columnDefinition="TEXT") private String cancelReason;
    @Column(name="cancelled_at") private OffsetDateTime cancelledAt;
    @Column(name="recurrence_rule",length=255) private String recurrenceRule;
    @OneToMany(mappedBy="meeting",cascade=CascadeType.ALL,orphanRemoval=true)
    private Set<MeetingParticipant> participants=new LinkedHashSet<>();
    @OneToMany(mappedBy="meeting",cascade=CascadeType.ALL,orphanRemoval=true)
    private List<MeetingEquipment> equipment=new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if(id==null)id=UUID.randomUUID().toString();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
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

    public String getRecurrenceSeriesId() {
        return recurrenceSeriesId;
    }

    public void setRecurrenceSeriesId(String recurrenceSeriesId) {
        this.recurrenceSeriesId = recurrenceSeriesId;
    }
    public String getRoom(){return room;} public void setRoom(String room){this.room=room;}
    public String getRecurrenceRule(){return recurrenceRule;} public void setRecurrenceRule(String value){recurrenceRule=value;}
    public Set<String> getParticipantIds(){return participants.stream().map(MeetingParticipant::getUserId).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));}
    public void setParticipantIds(Collection<String> values){participants.clear();if(values!=null)values.forEach(v->participants.add(new MeetingParticipant(this,v)));}
    public List<MeetingEquipment> getEquipment(){return equipment;} public void setEquipment(Collection<MeetingEquipment> values){equipment.clear();if(values!=null)values.forEach(v->{v.setMeeting(this);equipment.add(v);});}
    public Boolean getRecurring(){return recurring;} public void setRecurring(Boolean value){recurring=value;}
    public String getCancelReason(){return cancelReason;} public void setCancelReason(String value){cancelReason=value;}
    public OffsetDateTime getCancelledAt(){return cancelledAt;} public void setCancelledAt(OffsetDateTime value){cancelledAt=value;}

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
