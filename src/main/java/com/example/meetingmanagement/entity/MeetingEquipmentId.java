package com.example.meetingmanagement.entity;
import java.io.Serializable;
import java.util.Objects;
public class MeetingEquipmentId implements Serializable {
    public String meeting; public String equipmentId;
    public MeetingEquipmentId(){}
    public boolean equals(Object o){return o instanceof MeetingEquipmentId x&&Objects.equals(meeting,x.meeting)&&Objects.equals(equipmentId,x.equipmentId);}
    public int hashCode(){return Objects.hash(meeting,equipmentId);}
}
