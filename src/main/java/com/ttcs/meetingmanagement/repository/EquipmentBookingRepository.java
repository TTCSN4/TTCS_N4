package com.ttcs.meetingmanagement.repository;
import com.ttcs.meetingmanagement.model.*;
import org.springframework.data.jpa.repository.*;import org.springframework.data.repository.query.Param;
import java.time.OffsetDateTime;import java.util.List;
public interface EquipmentBookingRepository extends JpaRepository<EquipmentBooking,EquipmentBookingId>{
    List<EquipmentBooking> findAllByEquipment_EquipmentId(String equipmentId);
    List<EquipmentBooking> findAllByMeeting_Id(String meetingId);
    default List<EquipmentBooking> findAllByMeetingId(String meetingId){return findAllByMeeting_Id(meetingId);}
    @Query("select coalesce(sum(b.quantity),0) from EquipmentBooking b where b.equipment.equipmentId=:equipmentId and b.meeting.status<>com.ttcs.meetingmanagement.model.MeetingStatus.CANCELLED and b.meeting.startTime<:endTime and b.meeting.endTime>:startTime")
    long reservedQuantity(@Param("equipmentId")String id,@Param("startTime")OffsetDateTime start,@Param("endTime")OffsetDateTime end);
}
