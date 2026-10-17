package com.ttcs.meetingmanagement.repository;

import com.ttcs.meetingmanagement.model.RoomRestriction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoomRestrictionRepository extends JpaRepository<RoomRestriction, String> {
    List<RoomRestriction> findByRoomId(String roomId);
    List<RoomRestriction> findByUserId(String userId);
}
