package com.ttcs.meetingmanagement.equipment.booking;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface US12MeetingEquipmentRepository extends JpaRepository<US12MeetingEquipment,US12MeetingEquipmentId> {
    List<US12MeetingEquipment> findById_MeetingId(String meetingId);
}
