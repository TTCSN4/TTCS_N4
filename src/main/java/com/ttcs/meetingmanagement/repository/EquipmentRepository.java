package com.ttcs.meetingmanagement.repository;

import com.ttcs.meetingmanagement.model.Equipment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EquipmentRepository extends JpaRepository<Equipment, Long> {
    List<Equipment> findByRoomIgnoreCaseOrderByNameAsc(String room);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select equipment from Equipment equipment where equipment.id = :id")
    Optional<Equipment> findByIdForUpdate(@Param("id") Long id);
}