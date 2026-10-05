package com.ttcs.meetingmanagement.equipment;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class MeetingEquipmentId implements Serializable {
    @Column(name = "meeting_id", nullable = false, length = 50)
    private String meetingId;

    @Column(name = "equipment_id", nullable = false, length = 50)
    private String equipmentId;

    public MeetingEquipmentId() {
    }

    public MeetingEquipmentId(String meetingId, String equipmentId) {
        this.meetingId = meetingId;
        this.equipmentId = equipmentId;
    }

    public String getMeetingId() {
        return meetingId;
    }

    public void setMeetingId(String meetingId) {
        this.meetingId = meetingId;
    }

    public String getEquipmentId() {
        return equipmentId;
    }

    public void setEquipmentId(String equipmentId) {
        this.equipmentId = equipmentId;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof MeetingEquipmentId that)) return false;
        return Objects.equals(meetingId, that.meetingId)
                && Objects.equals(equipmentId, that.equipmentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(meetingId, equipmentId);
    }
}
