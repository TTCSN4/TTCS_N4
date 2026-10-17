package com.example.meetingmanagement.entity;
import jakarta.persistence.*;
@Embeddable public class MeetingEquipment {
    @Column(name="equipment_type",length=100) private String type;
    @Column(nullable=false) private int quantity;
    protected MeetingEquipment(){} public MeetingEquipment(String type,int quantity){this.type=type;this.quantity=quantity;}
    public String getType(){return type;} public int getQuantity(){return quantity;}
}
