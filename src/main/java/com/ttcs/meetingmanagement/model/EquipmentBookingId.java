package com.ttcs.meetingmanagement.model;
import java.io.Serializable;
import java.util.Objects;
public class EquipmentBookingId implements Serializable {
    public String meetingId;
    public String equipmentId;
    public EquipmentBookingId() {}
    public EquipmentBookingId(String meetingId,String equipmentId){this.meetingId=meetingId;this.equipmentId=equipmentId;}
    public boolean equals(Object o){return o instanceof EquipmentBookingId k && Objects.equals(meetingId,k.meetingId)&&Objects.equals(equipmentId,k.equipmentId);}
    public int hashCode(){return Objects.hash(meetingId,equipmentId);}
}
