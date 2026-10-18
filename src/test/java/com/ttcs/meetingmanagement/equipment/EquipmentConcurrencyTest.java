package com.ttcs.meetingmanagement.equipment;
import com.ttcs.meetingmanagement.model.Meeting;
import com.ttcs.meetingmanagement.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:equipment_concurrency;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","spring.jpa.hibernate.ddl-auto=create-drop","app.reminder.enabled=false"})
class EquipmentConcurrencyTest {
    @Autowired EquipmentRepository equipment;
    @Autowired MeetingRepository meetings;
    @Autowired EquipmentBookingRepository bookings;
    @Autowired EquipmentReservationService service;
    @Autowired PlatformTransactionManager transactions;
    @Test void simultaneousReservationsNeverExceedInventory()throws Exception{
        String eq=UUID.randomUUID().toString();String[] ids={UUID.randomUUID().toString(),UUID.randomUUID().toString()};
        new TransactionTemplate(transactions).execute(status->{Equipment e=new Equipment();e.setEquipmentId(eq);e.setRoomId("R");e.setEquipmentName("Projector");e.setType("TV");e.setTotalQuantity(1);e.setStatus("AVAILABLE");equipment.saveAndFlush(e);
            for(String id:ids){Meeting m=new Meeting();m.meetingId=id;m.organizerId="U";m.roomId="R";m.title="Concurrent";m.status="SCHEDULED";m.startTime=LocalDateTime.of(2099,1,1,9,0);m.endTime=m.startTime.plusHours(1);meetings.saveAndFlush(m);}return null;});
        CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> results=new ArrayList<>();
            for(String id:ids)results.add(pool.submit(()->{ready.countDown();start.await();try{new TransactionTemplate(transactions).execute(status->{Meeting m=meetings.findByIdForUpdate(id).orElseThrow();service.replace(m,List.of(new EquipmentReservationService.Item(eq,1)));return null;});return true;}catch(RuntimeException failure){return false;}}));
            assertTrue(ready.await(5,TimeUnit.SECONDS));start.countDown();int success=0;for(var result:results)if(result.get(20,TimeUnit.SECONDS))success++;
            assertEquals(1,success);assertEquals(1,bookings.findAllByEquipment_EquipmentId(eq).stream().mapToInt(b->b.getQuantity()).sum());
        } finally {start.countDown();pool.shutdownNow();}
    }
}
