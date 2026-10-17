package com.example.meetingmanagement.entity;
import jakarta.persistence.*;
@Entity @Table(name="meeting_equipment") @IdClass(MeetingEquipmentId.class) public class MeetingEquipment {
    @Id @ManyToOne @JoinColumn(name="meeting_id") private Meeting meeting;
    @Id @Column(name="equipment_id",length=255) private String equipmentId;
    @Column(nullable=false) private int quantity;
    protected MeetingEquipment(){} public MeetingEquipment(String equipmentId,int quantity){this.equipmentId=equipmentId;this.quantity=quantity;}
    public String getEquipmentId(){return equipmentId;} public int getQuantity(){return quantity;} public void setMeeting(Meeting value){meeting=value;}
}
