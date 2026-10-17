package com.ttcs.meetingmanagement.repository;

import com.ttcs.meetingmanagement.model.MeetingEquipment;
import com.ttcs.meetingmanagement.model.MeetingEquipmentId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MeetingEquipmentRepository extends JpaRepository<MeetingEquipment, MeetingEquipmentId> {
    List<MeetingEquipment> findById_MeetingId(String meetingId);
    List<MeetingEquipment> findById_EquipmentId(String equipmentId);
}
