package com.ttcs.meetingmanagement.repository;

import com.ttcs.meetingmanagement.model.MeetingEquipment;
import com.ttcs.meetingmanagement.model.MeetingEquipmentId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MeetingEquipmentRepository extends JpaRepository<MeetingEquipment, MeetingEquipmentId> {
    List<MeetingEquipment> findById_MeetingId(String meetingId);
    void deleteById_MeetingId(String meetingId);
}
