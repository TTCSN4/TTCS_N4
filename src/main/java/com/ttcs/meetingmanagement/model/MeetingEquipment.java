package com.ttcs.meetingmanagement.model;

import jakarta.persistence.*;

@Entity
@Table(name = "meeting_equipment")
public class MeetingEquipment {

    @EmbeddedId
    private MeetingEquipmentId id;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    public MeetingEquipment() {
    }

    public MeetingEquipment(MeetingEquipmentId id, int quantity) {
        this.id = id;
        this.quantity = quantity;
    }

    public MeetingEquipment(String meetingId, String equipmentId, int quantity) {
        this.id = new MeetingEquipmentId(meetingId, equipmentId);
        this.quantity = quantity;
    }

    public MeetingEquipmentId getId() {
        return id;
    }

    public void setId(MeetingEquipmentId id) {
        this.id = id;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public String getMeetingId() {
        return id != null ? id.getMeetingId() : null;
    }

    public String getEquipmentId() {
        return id != null ? id.getEquipmentId() : null;
    }
}
