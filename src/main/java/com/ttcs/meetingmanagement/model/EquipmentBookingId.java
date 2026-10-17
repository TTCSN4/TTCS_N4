package com.ttcs.meetingmanagement.model;
import java.io.Serializable;import java.util.Objects;
public class EquipmentBookingId implements Serializable {public String meeting;public String equipment;public EquipmentBookingId(){}public EquipmentBookingId(String m,String e){meeting=m;equipment=e;}public boolean equals(Object o){return o instanceof EquipmentBookingId x&&Objects.equals(meeting,x.meeting)&&Objects.equals(equipment,x.equipment);}public int hashCode(){return Objects.hash(meeting,equipment);}}
