package com.ttcs.meetingmanagement.model;
import com.ttcs.meetingmanagement.equipment.Equipment;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.time.ZoneId;
/** ERD meeting_equipment: time, room and cancellation come from the meeting. */
@Entity @Table(name="meeting_equipment") @IdClass(EquipmentBookingId.class)
public class EquipmentBooking {
    @Id @Column(name="meeting_id",length=255) private String meetingId;
    @Id @Column(name="equipment_id",length=255) private String equipmentId;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="meeting_id",insertable=false,updatable=false) private Meeting meeting;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="equipment_id",insertable=false,updatable=false) private Equipment equipment;
    @Column(name="quantity",nullable=false) private int quantity;
    public EquipmentBookingId getId(){return new EquipmentBookingId(meetingId,equipmentId);}
    public String getMeetingId(){return meetingId;}
    public void setMeetingId(String id){meetingId=id;}
    public void setMeeting(Meeting value){meeting=value;meetingId=value.getMeetingId();}
    public Meeting getMeeting(){return meeting;}
    public Equipment getEquipment(){return equipment;}
    public void setEquipment(Equipment value){equipment=value;equipmentId=value.getEquipmentId();}
    public int getQuantity(){return quantity;}
    public void setQuantity(int value){quantity=value;}
    public String getRoom(){return meeting.getRoomId();}
    public OffsetDateTime getStartTime(){return meeting.getStartTime().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toOffsetDateTime();}
    public OffsetDateTime getEndTime(){return meeting.getEndTime().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toOffsetDateTime();}
    public EquipmentBookingStatus getStatus(){return "CANCELLED".equals(meeting.getStatus())?EquipmentBookingStatus.CANCELLED:EquipmentBookingStatus.BOOKED;}
}
