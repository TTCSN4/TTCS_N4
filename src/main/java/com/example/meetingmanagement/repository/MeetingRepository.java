package com.example.meetingmanagement.repository;

import com.example.meetingmanagement.entity.Meeting;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MeetingRepository extends JpaRepository<Meeting, Long> {

    List<Meeting> findAllByOrderByIdAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select meeting from Meeting meeting where meeting.id = :id")
    Optional<Meeting> findByIdForUpdate(@Param("id") Long id);
}
