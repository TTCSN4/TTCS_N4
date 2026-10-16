package com.ttcs.meetingmanagement.recurrence;
<<<<<<< HEAD

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class RecurringScheduleCalculator {

    public record Occurrence(
            OffsetDateTime startTime,
            OffsetDateTime endTime
    ) {
    }

    public List<Occurrence> generate(
            OffsetDateTime startTime,
            OffsetDateTime endTime,
            RecurrenceFrequency frequency,
            int interval,
            int occurrences
    ) {

        // 1. Kiểm tra dữ liệu đầu vào
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException(
                    "Thời gian bắt đầu và kết thúc không được để trống"
            );
        }

        if (frequency == null) {
            throw new IllegalArgumentException(
                    "Tần suất lặp không được để trống"
            );
        }

        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException(
                    "Thời gian kết thúc phải sau thời gian bắt đầu"
            );
        }

        if (interval < 1) {
            throw new IllegalArgumentException(
                    "Khoảng lặp phải lớn hơn hoặc bằng 1"
            );
        }

        if (occurrences < 1) {
            throw new IllegalArgumentException(
                    "Số lần họp phải lớn hơn hoặc bằng 1"
            );
        }

        // 2. Tính thời lượng cuộc họp
        Duration duration = Duration.between(
                startTime,
                endTime
        );

        List<Occurrence> result = new ArrayList<>();

        // 3. Sinh các lần họp
        for (int i = 0; i < occurrences; i++) {

            long step = (long) i * interval;

            OffsetDateTime nextStart;

            if (frequency == RecurrenceFrequency.WEEKLY) {
                nextStart = startTime.plusWeeks(step);
            } else {
                nextStart = startTime.plusMonths(step);
            }

            OffsetDateTime nextEnd =
                    nextStart.plus(duration);

            result.add(
                    new Occurrence(nextStart, nextEnd)
            );
        }

        // 4. Trả về danh sách lịch họp
        return result;
    }
}
=======
import com.ttcs.meetingmanagement.dto.RecurringMeetingRequest;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
public class RecurringScheduleCalculator {
 public record Occurrence(OffsetDateTime startTime,OffsetDateTime endTime){}
 public List<Occurrence> generate(RecurringMeetingRequest r){
  if(r.startTime()==null||r.endTime()==null||r.frequency()==null)throw new IllegalArgumentException("Missing start, end or frequency.");
  var start=r.startTime().atZoneSameInstant(ZoneId.of("Asia/Ho_Chi_Minh"));
  var end=r.endTime().atZoneSameInstant(ZoneId.of("Asia/Ho_Chi_Minh"));
  if(!end.isAfter(start)||!start.toLocalDate().equals(end.toLocalDate()))throw new IllegalArgumentException("End must be after start on the same day.");
  int interval=r.interval()==null?1:r.interval();
  if(interval<1||interval>52)throw new IllegalArgumentException("Interval must be 1..52.");
  if((r.until()==null)==(r.occurrences()==null))throw new IllegalArgumentException("Choose until date OR occurrence count.");
  if(r.occurrences()!=null&&(r.occurrences()<1||r.occurrences()>100))throw new IllegalArgumentException("Count must be 1..100.");
  LocalDate first=start.toLocalDate(),limit=r.until()==null?first.plusYears(100):r.until();
  if(r.until()!=null&&(limit.isBefore(first)||limit.isAfter(first.plusYears(2))))throw new IllegalArgumentException("Until must be within two years after start.");
  List<Integer> weekdays=r.weekdays()==null?List.of(start.getDayOfWeek().getValue()):r.weekdays();
  if(r.frequency()==RecurrenceFrequency.WEEKLY&&(weekdays.isEmpty()||weekdays.stream().anyMatch(d->d==null||d<1||d>7)||weekdays.stream().distinct().count()!=weekdays.size()))throw new IllegalArgumentException("Choose valid unique weekdays.");
  int monthDay=r.monthDay()==null?first.getDayOfMonth():r.monthDay();
  if(monthDay<1||monthDay>31)throw new IllegalArgumentException("Month day must be 1..31.");
  LocalDate anchor=first.minusDays(first.getDayOfWeek().getValue()-1);List<Occurrence> result=new ArrayList<>();
  for(LocalDate d=first;!d.isAfter(limit);d=d.plusDays(1)){
   boolean match=r.frequency()==RecurrenceFrequency.WEEKLY?ChronoUnit.WEEKS.between(anchor,d.minusDays(d.getDayOfWeek().getValue()-1))%interval==0&&weekdays.contains(d.getDayOfWeek().getValue()):ChronoUnit.MONTHS.between(YearMonth.from(first),YearMonth.from(d))%interval==0&&d.getDayOfMonth()==monthDay;
   if(match){result.add(new Occurrence(d.atTime(start.toLocalTime()).atZone(start.getZone()).toOffsetDateTime(),d.atTime(end.toLocalTime()).atZone(start.getZone()).toOffsetDateTime()));if(result.size()>100)throw new IllegalArgumentException("Series exceeds 100 meetings.");if(r.occurrences()!=null&&result.size()==r.occurrences())break;}
  }
  if(result.isEmpty()||(r.occurrences()!=null&&result.size()!=r.occurrences()))throw new IllegalArgumentException("No matching dates or count exceeds scheduling horizon.");
  return result;
 }
 // Retain the original calculator entry point for existing callers.
 public List<Occurrence> generate(OffsetDateTime start,OffsetDateTime end,RecurrenceFrequency frequency,int interval,int count){
  return generate(new RecurringMeetingRequest(null,"room","meeting",null,start,end,frequency,interval,count,null,null,null,List.of()));
 }
}
>>>>>>> parent of 64bacae (chore(US03): remove backend files and preserve directories)
