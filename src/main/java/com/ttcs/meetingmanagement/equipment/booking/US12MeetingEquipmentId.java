package com.ttcs.meetingmanagement.equipment.booking;
import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class US12MeetingEquipmentId implements Serializable {
    @Column(name="meeting_id", length=50) public String meetingId;
    @Column(name="equipment_id", length=50) public String equipmentId;
    public US12MeetingEquipmentId() {}
    public US12MeetingEquipmentId(String meeting, String equipment) { meetingId=meeting; equipmentId=equipment; }
    @Override public boolean equals(Object o) { return o instanceof US12MeetingEquipmentId x && Objects.equals(meetingId,x.meetingId) && Objects.equals(equipmentId,x.equipmentId); }
    @Override public int hashCode() { return Objects.hash(meetingId,equipmentId); }
}
