package com.ttcs.meetingmanagement.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "meeting_equipment")
public class MeetingEquipment {

    @EmbeddedId
    private MeetingEquipmentId id;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    public MeetingEquipment() {
        this.id = new MeetingEquipmentId();
    }

    public MeetingEquipment(MeetingEquipmentId id, Integer quantity) {
        this.id = id;
        this.quantity = quantity;
    }

    public MeetingEquipment(String meetingId, String equipmentId, Integer quantity) {
        this.id = new MeetingEquipmentId(meetingId, equipmentId);
        this.quantity = quantity;
    }

    public MeetingEquipmentId getId() {
        return id;
    }

    public void setId(MeetingEquipmentId id) {
        this.id = id;
    }

    public String getMeetingId() {
        return id != null ? id.getMeetingId() : null;
    }

    public void setMeetingId(String meetingId) {
        if (this.id == null) {
            this.id = new MeetingEquipmentId();
        }
        this.id.setMeetingId(meetingId);
    }

    public String getEquipmentId() {
        return id != null ? id.getEquipmentId() : null;
    }

    public void setEquipmentId(String equipmentId) {
        if (this.id == null) {
            this.id = new MeetingEquipmentId();
        }
        this.id.setEquipmentId(equipmentId);
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}
