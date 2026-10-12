package com.ttcs.meetingmanagement.history;
import org.springframework.data.jpa.repository.*;
import java.util.List;
public interface HistoryCheckInRepository extends JpaRepository<HistoryCheckIn,Long>{
 @Query("select distinct c.meetingId from HistoryCheckIn c where c.userId=:userId and c.checkInTime is not null")
 List<Long> checkedMeetingIds(String userId);
}
