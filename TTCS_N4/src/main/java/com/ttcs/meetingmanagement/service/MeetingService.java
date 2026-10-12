package com.ttcs.meetingmanagement.service;
import com.ttcs.meetingmanagement.controller.MeetingController.MeetingRequest;
import com.ttcs.meetingmanagement.model.*;
import com.ttcs.meetingmanagement.repository.*;
import com.ttcs.meetingmanagement.room.Room;
import com.ttcs.meetingmanagement.equipment.*;
import jakarta.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;import java.util.*;
@Service
public class MeetingService {
 private final EntityManager em;private final MeetingRepository meetings;private final EquipmentRepository equipment;private final EquipmentBookingRepository bookings;private final EquipmentController equipmentController;
 public MeetingService(EntityManager em,MeetingRepository meetings,EquipmentRepository equipment,EquipmentBookingRepository bookings,EquipmentController equipmentController){this.em=em;this.meetings=meetings;this.equipment=equipment;this.bookings=bookings;this.equipmentController=equipmentController;}
 private void require(boolean ok,String reason){if(!ok)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,reason);}
 @Transactional public Meeting create(MeetingRequest r){
  require(r.title()!=null&&!r.title().isBlank()&&r.title().length()<=200,"Nhập tiêu đề từ 1 đến 200 ký tự.");
  require(r.description()==null||r.description().length()<=500,"Mô tả không quá 500 ký tự.");
  require(r.notes()==null||r.notes().length()<=500,"Ghi chú không quá 500 ký tự.");
  require(r.startTime()!=null&&r.endTime()!=null&&r.endTime().isAfter(r.startTime()),"Thời gian kết thúc phải sau thời gian bắt đầu.");
  require(r.startTime().isAfter(OffsetDateTime.now()),"Thời gian bắt đầu phải trong tương lai.");
  List<String> participants=r.participants()==null?List.of():r.participants().stream().map(x->x==null?"":x.trim().toLowerCase(Locale.ROOT)).toList();
  require(participants.stream().allMatch(x->x.length()<=254&&x.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")),"Email người tham dự không hợp lệ.");
  require(participants.stream().distinct().count()==participants.size(),"Người tham dự bị trùng.");
  Room room=null;
  if(r.room()!=null&&!r.room().isBlank()){
   room=em.find(Room.class,r.room().trim(),LockModeType.PESSIMISTIC_WRITE);
   if(room==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy phòng họp.");
   require(!Set.of("MAINTENANCE","INACTIVE","UNAVAILABLE").contains(room.getStatus().toUpperCase(Locale.ROOT)),"Phòng không khả dụng.");
   require(room.getCapacity()!=null&&room.getCapacity()>=participants.size()+1,"Số người (bao gồm người tổ chức) vượt sức chứa phòng.");
  }
  List<OffsetDateTime> starts=new ArrayList<>();starts.add(r.startTime());int repeats=r.repeatCount()==null?0:r.repeatCount();
  require(repeats>=0&&repeats<=12,"Số lần lặp thêm từ 0 đến 12.");
  String frequency=r.recurrence()==null?"NONE":r.recurrence().toUpperCase(Locale.ROOT);
  if(repeats>0){require(Set.of("WEEKLY","MONTHLY").contains(frequency),"Kiểu lặp không hợp lệ.");for(int n=1;n<=repeats;n++)starts.add(frequency.equals("WEEKLY")?r.startTime().plusWeeks(n):r.startTime().plusMonths(n));}
  var duration=Duration.between(r.startTime(),r.endTime());
  for(var start:starts){
   var end=start.plus(duration);
   if(room!=null&&meetings.countRoomConflicts(room.getRoomId(),-1L,MeetingStatus.CANCELLED,start,end)>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Phòng đã được đặt vào "+start+".");
   if(meetings.findAllByOrderByStartTimeAsc().stream().filter(m->m.getStatus()!=MeetingStatus.CANCELLED&&m.getStartTime().isBefore(end)&&m.getEndTime().isAfter(start)).anyMatch(m->m.getParticipants().stream().map(String::toLowerCase).anyMatch(participants::contains)))throw new ResponseStatusException(HttpStatus.CONFLICT,"Người tham dự đã có lịch trong khung giờ này.");
  }
  List<String> ids=r.equipmentIds()==null?List.of():r.equipmentIds();require(ids.stream().distinct().count()==ids.size(),"Thiết bị bị chọn trùng.");
  Meeting first=null;
  for(var start:starts){Meeting m=new Meeting();m.setTitle(r.title().trim());m.setDescription(r.description());m.setNotes(r.notes());m.setRoom(room==null?null:room.getRoomId());m.setStartTime(start);m.setEndTime(start.plus(duration));m.setOrganizerId(r.organizerId()==null?1L:r.organizerId());m.setStatus(MeetingStatus.SCHEDULED);m.setParticipants(new ArrayList<>(participants));m.setCreatedAt(OffsetDateTime.now());m.setUpdatedAt(OffsetDateTime.now());meetings.save(m);if(first==null)first=m;if(repeats>0)m.setRecurrenceSeriesId(first.getId());
   for(String id:ids.stream().sorted().toList()){
    Equipment e=equipment.findByIdForUpdate(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy thiết bị."));
    require(!Set.of("MAINTENANCE","BROKEN","UNAVAILABLE").contains(e.getStatus().toUpperCase(Locale.ROOT)),"Thiết bị không khả dụng.");
    equipmentController.book(new EquipmentController.EquipmentBookingRequest(id,m.getId(),m.getRoom(),1,m.getStartTime(),m.getEndTime()));
   }
  }
  return first;
 }
 public record Device(String id,String name,boolean available,int availableQuantity){}
 public record Availability(boolean available,int capacity,String status,List<Device> equipment){}
 @Transactional(readOnly=true) public Availability availability(String roomId,OffsetDateTime start,OffsetDateTime end){
  require(start!=null&&end!=null&&end.isAfter(start),"Chọn thời gian hợp lệ.");
  Room r=em.find(Room.class,roomId);if(r==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy phòng.");
  boolean free=!Set.of("MAINTENANCE","INACTIVE","UNAVAILABLE").contains(r.getStatus().toUpperCase(Locale.ROOT))&&meetings.countRoomConflicts(roomId,-1L,MeetingStatus.CANCELLED,start,end)==0;
  List<Device> devices=equipment.findByRoomIdIgnoreCaseOrderByEquipmentNameAsc(roomId).stream().map(e->{
   TreeMap<Instant,Integer> events=new TreeMap<>();
   for(var b:bookings.findAllByEquipment_EquipmentId(e.getEquipmentId()))if(b.getStatus()!=EquipmentBookingStatus.CANCELLED&&b.getStartTime().isBefore(end)&&b.getEndTime().isAfter(start)){
    events.merge((b.getStartTime().isBefore(start)?start:b.getStartTime()).toInstant(),b.getQuantity(),Integer::sum);events.merge((b.getEndTime().isAfter(end)?end:b.getEndTime()).toInstant(),-b.getQuantity(),Integer::sum);}
   int used=0,peak=0;for(int delta:events.values()){used+=delta;peak=Math.max(peak,used);}
   int left=Math.max(0,(e.getTotalQuantity()==null?0:e.getTotalQuantity())-peak);
   boolean ready=left>0&&!Set.of("MAINTENANCE","BROKEN","UNAVAILABLE").contains(e.getStatus().toUpperCase(Locale.ROOT));
   return new Device(e.getEquipmentId(),e.getEquipmentName(),ready,left);
  }).toList();
  return new Availability(free,r.getCapacity()==null?0:r.getCapacity(),r.getStatus(),devices);
 }
}
