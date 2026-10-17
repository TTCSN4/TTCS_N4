package com.ttcs.meetingmanagement.model;
import com.ttcs.meetingmanagement.equipment.Equipment;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
@Entity @Table(name="meeting_equipment") @IdClass(EquipmentBookingId.class)
public class EquipmentBooking {
    @Id @ManyToOne(optional=false) @JoinColumn(name="meeting_id") private Meeting meeting;
    @Id @ManyToOne(optional=false) @JoinColumn(name="equipment_id") private Equipment equipment;
    @Column(nullable=false) private int quantity;
    public EquipmentBooking(){}
    public String getId(){return getMeetingId()+":"+(equipment==null?"":equipment.getEquipmentId());}
    public Equipment getEquipment(){return equipment;} public void setEquipment(Equipment value){equipment=value;}
    public Meeting getMeeting(){return meeting;} public void setMeeting(Meeting value){meeting=value;}
    public String getMeetingId(){return meeting==null?null:meeting.getId();}
    public String getRoom(){return meeting==null?null:meeting.getRoom();}
    public int getQuantity(){return quantity;} public void setQuantity(int value){quantity=value;}
    public OffsetDateTime getStartTime(){return meeting.getStartTime();} public OffsetDateTime getEndTime(){return meeting.getEndTime();}
    public EquipmentBookingStatus getStatus(){return meeting!=null&&meeting.getStatus()==MeetingStatus.CANCELLED?EquipmentBookingStatus.CANCELLED:EquipmentBookingStatus.BOOKED;}
}
