package com.ttcs.meetingmanagement.chatbot;
import com.ttcs.meetingmanagement.model.*;
import com.ttcs.meetingmanagement.room.Room;
import com.ttcs.meetingmanagement.repository.*;
import com.ttcs.meetingmanagement.service.RoomBookingPermissionService;
import com.ttcs.meetingmanagement.service.RoomCapacityValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
@Service
class US25ChatbotBookingService {
    private final US25ChatRoomRepository rooms;
    private final MeetingRepository meetings;
    private final UserRepository users;
    private final MeetingParticipantRepository participants;
    private final RoomBookingPermissionService permissions;
    US25ChatbotBookingService(US25ChatRoomRepository rooms,MeetingRepository meetings,UserRepository users,
    MeetingParticipantRepository participants,RoomBookingPermissionService permissions) {
        this.rooms=rooms;
        this.meetings=meetings;
        this.users=users;
        this.participants=participants;
        this.permissions=permissions;
    }
    List<US25AvailableRoom> availableRooms(LocalDateTime start,LocalDateTime end,int attendees) {
        if(start==null||end==null||!end.isAfter(start)||attendees<1)return List.of();
        return rooms.findAll().stream().filter(r->r.status!=null
                &&List.of("ACTIVE","AVAILABLE").contains(r.status.trim().toUpperCase(Locale.ROOT))
                &&r.capacity!=null&&r.capacity>0&&r.capacity>=attendees)
        .filter(r->meetings.countRoomConflicts(r.roomId,null,start,end)==0)
        .sorted(Comparator.comparingInt(r->r.capacity)).map(US25AvailableRoom::of).toList();
    }
    @Transactional
    US25ChatBookingResponse book(US25ChatBookingRequest q) {
        if(q.startTime()==null||q.endTime()==null||!q.endTime().isAfter(q.startTime())||!q.startTime().isAfter(LocalDateTime.now())||q.attendees()<1)
        throw error(HttpStatus.BAD_REQUEST,"Thời gian hoặc số người tham dự không hợp lệ");
        if(Boolean.TRUE.equals(q.recurring()))throw error(HttpStatus.BAD_REQUEST,"Tạo lịch định kỳ qua API /api/meetings/recurring");
        activeUser(q.organizerId());
        List<String> invited=Optional.ofNullable(q.participantIds()).orElse(List.of()).stream().distinct().toList();
        if(invited.size()+1>q.attendees())throw error(HttpStatus.BAD_REQUEST,"Số người tham dự phải bao gồm người tổ chức và người được mời");
        invited.forEach(this::activeUser);
        if(q.roomId()!=null&&!q.roomId().isBlank()) {
            Room requestedRoom=rooms.findLockedById(q.roomId())
                    .orElseThrow(()->error(HttpStatus.NOT_FOUND,"Không tìm thấy phòng"));
            RoomCapacityValidator.validate(requestedRoom.capacity, q.attendees());
        }
        Set<String> members=new HashSet<>(invited);
        members.add(q.organizerId());
        for(var existing:meetings.findAll()) {
            if("CANCELLED".equals(existing.status)||!existing.startTime.isBefore(q.endTime())||!existing.endTime.isAfter(q.startTime()))continue;
            if(members.contains(existing.organizerId)||participants.findByMeetingId(existing.meetingId).stream()
            .filter(p->!Set.of("DECLINED","REMOVED").contains(p.status)).anyMatch(p->members.contains(p.userId)))
            throw error(HttpStatus.CONFLICT,"Người tổ chức hoặc người tham dự bị trùng lịch");
        }
        List<Room> candidates=q.roomId()!=null&&!q.roomId().isBlank()
        ?List.of(rooms.findLockedById(q.roomId()).orElseThrow(()->error(HttpStatus.NOT_FOUND,"Không tìm thấy phòng")))
        :rooms.findAvailableForBooking(q.attendees());
        Room chosen=null;
        for(Room room:candidates) {
            if(room.status==null||!List.of("ACTIVE","AVAILABLE").contains(room.status.trim().toUpperCase(Locale.ROOT))
            ||room.capacity==null||room.capacity<=0||room.capacity<q.attendees()
            ||meetings.countRoomConflicts(room.roomId,null,q.startTime(),q.endTime())>0)continue;
            try {
                permissions.assertCanBook(q.organizerId(),room.roomId);
            }
            catch(ResponseStatusException ex) {
                if(q.roomId()!=null&&!q.roomId().isBlank()||ex.getStatusCode().value()!=403)throw ex;
                else continue;
            }
            chosen=room;
            break;
        }
        if(chosen==null)throw error(HttpStatus.CONFLICT,"Không còn phòng phù hợp hoặc bạn không có quyền đặt phòng");
        Meeting m=new Meeting();
        m.organizerId=q.organizerId();
        m.roomId=chosen.roomId;
        m.title=q.title().trim();
        m.description=q.description();
        m.startTime=q.startTime();
        m.endTime=q.endTime();
        m.isRecurring=false;
        meetings.saveAndFlush(m);
        for(String id:invited) {
            if(id.equals(q.organizerId()))continue;
            MeetingParticipant p=new MeetingParticipant();
            p.meetingId=m.meetingId;
            p.userId=id;
            p.status="INVITED";
            participants.save(p);
        }
        return new US25ChatBookingResponse("Đã đặt "+chosen.roomName+" thành công",m.meetingId,chosen.roomId,chosen.roomName,m.startTime,m.endTime);
    }
    private void activeUser(String id) {
        User user=users.findById(id).orElseThrow(()->error(HttpStatus.BAD_REQUEST,"Người dùng không tồn tại: "+id));
        if(!"ACTIVE".equalsIgnoreCase(user.status))throw error(HttpStatus.BAD_REQUEST,"Người dùng đã bị vô hiệu hóa: "+id);
    }
    private static ResponseStatusException error(HttpStatus status,String message) {
        return new ResponseStatusException(status,message);
    }
}
