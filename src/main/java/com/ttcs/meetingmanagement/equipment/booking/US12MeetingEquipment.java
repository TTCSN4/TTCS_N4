package com.ttcs.meetingmanagement.equipment.booking;
import jakarta.persistence.*;

@Entity @Table(name="meeting_equipment")
public class US12MeetingEquipment {
    @EmbeddedId public US12MeetingEquipmentId id;
    @Column(nullable=false) public int quantity;
    public US12MeetingEquipment() {}
    public US12MeetingEquipment(String meeting,String equipment,int quantity) {
        id=new US12MeetingEquipmentId(meeting,equipment); this.quantity=quantity;
    }
}
