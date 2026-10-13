package com.ttcs.meetingmanagement;
import com.ttcs.meetingmanagement.dto.RecurringMeetingRequest;
import com.ttcs.meetingmanagement.recurrence.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class RecurringScheduleTest {
 RecurringMeetingRequest request(RecurrenceFrequency f,int interval,Integer count,List<Integer> days,Integer monthDay,LocalDate until){
 return new RecurringMeetingRequest("USER-1","R1","Test meeting",null,OffsetDateTime.parse("2099-01-05T09:00:00+07:00"),OffsetDateTime.parse("2099-01-05T10:00:00+07:00"),f,interval,count,days,monthDay,until,List.of());
 }
 @Test void weeklyMultipleDaysAndInterval(){var result=new RecurringScheduleCalculator().generate(request(RecurrenceFrequency.WEEKLY,2,4,List.of(1,3),null,null));assertEquals(List.of(LocalDate.of(2099,1,5),LocalDate.of(2099,1,7),LocalDate.of(2099,1,19),LocalDate.of(2099,1,21)),result.stream().map(s->s.startTime().toLocalDate()).toList());}
 @Test void monthlySkipsMissingDay(){var result=new RecurringScheduleCalculator().generate(request(RecurrenceFrequency.MONTHLY,1,3,null,31,null));assertEquals(List.of(LocalDate.of(2099,1,31),LocalDate.of(2099,3,31),LocalDate.of(2099,5,31)),result.stream().map(s->s.startTime().toLocalDate()).toList());}
 @Test void untilAndInvalidConfiguration(){var c=new RecurringScheduleCalculator();assertEquals(2,c.generate(request(RecurrenceFrequency.WEEKLY,1,null,List.of(1),null,LocalDate.of(2099,1,12))).size());assertThrows(IllegalArgumentException.class,()->c.generate(request(RecurrenceFrequency.WEEKLY,1,null,List.of(1),null,LocalDate.of(2098,1,1))));assertThrows(IllegalArgumentException.class,()->c.generate(request(RecurrenceFrequency.WEEKLY,1,4,List.of(),null,null)));assertThrows(IllegalArgumentException.class,()->c.generate(request(RecurrenceFrequency.MONTHLY,0,4,null,1,null)));}
}
