package com.ttcs.meetingmanagement.chatbot;
import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import tools.jackson.databind.*;
import com.ttcs.meetingmanagement.model.*;
import com.ttcs.meetingmanagement.room.*;
import com.ttcs.meetingmanagement.room.Room;
import com.ttcs.meetingmanagement.repository.*;
@Service class US25ChatConversationService {
    enum Step {
        ORGANIZER,TITLE,START,END,ATTENDEES,PARTICIPANTS,ROOM,CONFIRM,DONE
    }
    static class State {
        Step step=Step.ORGANIZER;
        String organizerId,title;
        LocalDateTime start,end;
        int attendees;
        List<String> participantIds=List.of();
        String roomId;
        List<US25AvailableRoom> roomOptions=List.of();
    }
    private final Map<String,State> sessions=new ConcurrentHashMap<>();
    private final Map<String,String> sessionOwners=new ConcurrentHashMap<>();
    synchronized US25ChatMessageResponse replyFor(String actor,US25ChatMessageRequest request) {
        String id=request.sessionId()==null||request.sessionId().isBlank()?UUID.randomUUID().toString():request.sessionId();
        String owner=sessionOwners.putIfAbsent(id,actor);
        if(owner!=null&&!owner.equals(actor))throw bad("Session belongs to another user");
        State state=sessions.computeIfAbsent(id,k->new State());
        if(state.organizerId==null) {
            state.organizerId=actor;
            state.step=Step.TITLE;
        }
        US25ChatMessageResponse result=reply(new US25ChatMessageRequest(id,request.message()));
        State updated=sessions.get(id);
        if(updated!=null&&updated.organizerId!=null&&!actor.equals(updated.organizerId)) {
            sessions.remove(id);
            throw bad("Organizer must match authenticated user");
        }
        return result;
    }
    private final UserRepository users;
    private final US25ChatRoomRepository rooms;
    private final US25ChatbotBookingService booking;
    private final US25GeminiIntentService ai;
    US25ChatConversationService(UserRepository u,US25ChatRoomRepository r,US25ChatbotBookingService b,US25GeminiIntentService ai) {
        users=u;
        rooms=r;
        booking=b;
        this.ai=ai;
    }
    US25ChatMessageResponse reply(US25ChatMessageRequest request) {
        String id=request.sessionId()==null||request.sessionId().isBlank()?UUID.randomUUID().toString():request.sessionId();
        String text=request.message().trim();
        if(isReset(text)) {
            sessions.put(id,new State());
            return answer(id,"Đã bắt đầu lại. Bạn có thể mô tả toàn bộ yêu cầu hoặc nhập mã người tổ chức.",Step.ORGANIZER,null);
        }
        State state=sessions.computeIfAbsent(id,k->new State());
        try {
            if((state.step==Step.ORGANIZER||state.step==Step.TITLE)&&looksNatural(text)) {
                Optional<US25AiBookingDraft> draft=ai.analyze(text).or(()->localDraft(text));
                if(draft.isPresent())return applyDraft(id,state,draft.get());
            }
            return advance(id,state,text);
        }
        catch(ResponseStatusException ex) {
            return answer(id,Objects.toString(ex.getReason(),"Không thể xử lý yêu cầu."),state.step,null);
        }
        catch(RuntimeException ex) {
            return answer(id,ex.getMessage(),state.step,null);
        }
    }
    private static Optional<US25AiBookingDraft> localDraft(String text) {
        String normalized=text.trim();
        String organizer=firstMatch(normalized,"(?i)\\b(user-[a-z0-9_-]+)\\b");
        Integer attendees=integerMatch(normalized,"(?i)(\\d+)\\s*người");
        String title=firstMatch(normalized,"(?i)đặt\\s+phòng(?:\\s+họp)?\\s+(.+?)\\s+cho\\s+\\d+\\s*người");
        List<String> ids=new ArrayList<>();
        var idMatcher=java.util.regex.Pattern.compile("(?i)\\buser-[a-z0-9_-]+\\b").matcher(normalized);
        while(idMatcher.find()) {
            String value=idMatcher.group().toLowerCase(Locale.ROOT);
            if(!value.equalsIgnoreCase(organizer)&&!ids.contains(value))ids.add(value);
        }
        LocalDate date=null;
        String dateText=firstMatch(normalized,"\\b(\\d{1,2}/\\d{1,2}/\\d{4})\\b");
        ZoneId zone=ZoneId.of("Asia/Ho_Chi_Minh");
        if(dateText!=null)try {
            date=LocalDate.parse(dateText,DateTimeFormatter.ofPattern("d/M/yyyy"));
        }
        catch(DateTimeParseException ignored) {
        }
        else if(normalized.toLowerCase(Locale.ROOT).contains("ngày mai"))date=LocalDate.now(zone).plusDays(1);
        var times=new ArrayList<LocalTime>();
        var timeMatcher=java.util.regex.Pattern.compile("(?<!\\d)([01]?\\d|2[0-3])[:h]([0-5]\\d)(?!\\d)",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(normalized);
        while(timeMatcher.find())times.add(LocalTime.of(Integer.parseInt(timeMatcher.group(1)),Integer.parseInt(timeMatcher.group(2))));
        LocalDateTime start=date!=null&&!times.isEmpty()?date.atTime(times.get(0)):null;
        LocalDateTime end=date!=null&&times.size()>1?date.atTime(times.get(1)):null;
        if(start!=null&&end==null) {
            Integer hours=integerMatch(normalized,"(?i)trong\\s+(\\d+)\\s*(?:tiếng|giờ)");
            if(hours!=null)end=start.plusHours(hours);
        }
        String room=firstMatch(normalized,"(?i)\\b(ROOM-[A-Z0-9_-]+)\\b");
        if(organizer==null&&attendees==null&&start==null)return Optional.empty();
        return Optional.of(new US25AiBookingDraft(organizer,title,start==null?null:start.toString(),end==null?null:end.toString(),attendees,room,ids,"Mình đã đọc yêu cầu và sẽ hỏi thêm những thông tin còn thiếu."));
    }
    private static String firstMatch(String text,String regex) {
        var matcher=java.util.regex.Pattern.compile(regex,java.util.regex.Pattern.CASE_INSENSITIVE|java.util.regex.Pattern.UNICODE_CASE).matcher(text);
        return matcher.find()?matcher.group(1).trim():null;
    }
    private static Integer integerMatch(String text,String regex) {
        String value=firstMatch(text,regex);
        return value==null?null:Integer.valueOf(value);
    }
    private US25ChatMessageResponse applyDraft(String id,State s,US25AiBookingDraft d) {
        if(d.organizerId()!=null) {
            User u=users.findById(d.organizerId()).orElseThrow(()->bad("AI nhận được mã người tổ chức '"+d.organizerId()+"' nhưng người này không tồn tại."));
            if(!"ACTIVE".equalsIgnoreCase(u.status))throw bad("Người tổ chức đã bị vô hiệu hóa.");
            s.organizerId=u.userId;
        }
        s.title=blankToNull(d.title());
        if(d.startTime()!=null)s.start=parseTime(d.startTime());
        if(d.endTime()!=null)s.end=parseTime(d.endTime());
        if(d.attendees()!=null)s.attendees=d.attendees();
        s.participantIds=d.participantIds()==null?List.of():d.participantIds();
        validateDraft(s);
        s.step=nextMissing(s);
        String prefix=d.reply()==null||d.reply().isBlank()?"":d.reply()+"\n";
        if(s.step!=Step.CONFIRM)return answer(id,prefix+promptFor(s),s.step,null);
        US25ChatMessageResponse choices=showAvailableRooms(id,s,prefix+"Mình đã truy vấn lịch trong cơ sở dữ liệu.");
        String requested=blankToNull(d.roomId());
        if(requested==null||s.roomOptions.stream().noneMatch(r->r.id().equalsIgnoreCase(requested)))return choices;
        s.roomId=requested.toUpperCase(Locale.ROOT);
        s.step=Step.CONFIRM;
        return answer(id,prefix+summary(s)+"\nNhập 'xác nhận' để đặt phòng hoặc 'làm lại' để bắt đầu lại.",s.step,null);
    }
    private static Step nextMissing(State s) {
        if(s.organizerId==null)return Step.ORGANIZER;
        if(s.title==null)return Step.TITLE;
        if(s.start==null)return Step.START;
        if(s.end==null)return Step.END;
        if(s.attendees<1)return Step.ATTENDEES;
        return Step.CONFIRM;
    }
    private String promptFor(State s) {
        return switch(s.step) {
            case ORGANIZER->"Hãy nhập mã người tổ chức.";
            case TITLE->"Cuộc họp có tiêu đề gì?";
            case START->"Cuộc họp bắt đầu lúc nào?";
            case END->"Cuộc họp kết thúc lúc nào?";
            case ATTENDEES->"Có bao nhiêu người tham dự?";
            default->"Hãy tiếp tục.";
        }
        ;
    }
    private US25ChatMessageResponse advance(String id,State s,String text) {
        switch(s.step) {
            case ORGANIZER-> {
                User u=users.findById(text).orElseThrow(()->bad("Không tìm thấy người dùng '"+text+"'. Hãy nhập lại mã người tổ chức."));
                if(!"ACTIVE".equalsIgnoreCase(u.status))throw bad("Tài khoản này đang bị vô hiệu hóa. Hãy chọn người tổ chức khác.");
                s.organizerId=u.userId;
                s.step=Step.TITLE;
                return answer(id,"Xin chào "+Objects.toString(u.fullName,u.userId)+". Cuộc họp có tiêu đề gì?",s.step,null);
            }
            case TITLE-> {
                s.title=text;
                s.step=Step.START;
                return answer(id,"Cuộc họp bắt đầu lúc nào? Nhập theo dạng dd/MM/yyyy HH:mm, ví dụ 12/10/2026 14:00.",s.step,null);
            }
            case START-> {
                s.start=parseTime(text);
                if(!s.start.isAfter(LocalDateTime.now()))throw bad("Thời gian bắt đầu phải ở tương lai. Hãy nhập lại.");
                s.step=Step.END;
                return answer(id,"Cuộc họp kết thúc lúc nào?",s.step,null);
            }
            case END-> {
                s.end=parseTime(text);
                if(!s.end.isAfter(s.start))throw bad("Thời gian kết thúc phải sau thời gian bắt đầu. Hãy nhập lại.");
                s.step=Step.ATTENDEES;
                return answer(id,"Có bao nhiêu người tham dự?",s.step,null);
            }
            case ATTENDEES-> {
                try {
                    s.attendees=Integer.parseInt(text);
                }
                catch(NumberFormatException e) {
                    throw bad("Số người phải là số nguyên dương.");
                }
                if(s.attendees<1)throw bad("Số người phải lớn hơn 0.");
                s.step=Step.PARTICIPANTS;
                return answer(id,"Nhập mã người tham dự, cách nhau bằng dấu phẩy; hoặc nhập 'không'.",s.step,null);
            }
            case PARTICIPANTS-> {
                s.participantIds=isSkip(text)?List.of():Arrays.stream(text.split(",")).map(String::trim).filter(x->!x.isBlank()).toList();
                validateParticipants(s);
                return showAvailableRooms(id,s,"Mình đã truy vấn lịch trong cơ sở dữ liệu.");
            }
            case ROOM-> {
                if(s.roomOptions.isEmpty())return showAvailableRooms(id,s,"Danh sách phòng đã được làm mới.");
                String selected=isAuto(text)?s.roomOptions.get(0).id():text.toUpperCase(Locale.ROOT);
                if(s.roomOptions.stream().noneMatch(r->r.id().equalsIgnoreCase(selected)))return answer(id,"Phòng '"+selected+"' không nằm trong danh sách còn trống. Hãy chọn một phòng bên dưới.",Step.ROOM,null,s.roomOptions);
                s.roomId=selected;
                s.step=Step.CONFIRM;
                return answer(id,summary(s)+"\nNhập 'xác nhận' để đặt phòng hoặc 'làm lại' để bắt đầu lại.",s.step,null);
            }
            case CONFIRM-> {
                if(!isConfirm(text))return answer(id,"Mình chưa đặt phòng. Hãy nhập 'xác nhận' hoặc 'làm lại'.",s.step,null);
                List<US25AvailableRoom> latest=booking.availableRooms(s.start,s.end,s.attendees);
                if(latest.stream().noneMatch(r->r.id().equals(s.roomId))) {
                    s.roomId=null;
                    return showAvailableRooms(id,s,"Phòng bạn chọn vừa không còn trống. Mình đã cập nhật danh sách, vui lòng chọn lại.");
                }
                US25ChatBookingResponse result=booking.book(new US25ChatBookingRequest(s.organizerId,s.title,null,s.start,s.end,s.attendees,s.roomId,s.participantIds,false,null));
                s.step=Step.DONE;
                return answer(id,result.message()+". Mã cuộc họp: "+result.meetingId()+".",s.step,result);
            }
            case DONE-> {
                return answer(id,"Yêu cầu này đã hoàn tất. Nhập 'làm lại' để đặt một phòng khác.",s.step,null);
            }
        }
        throw bad("Trạng thái hội thoại không hợp lệ.");
    }
    private US25ChatMessageResponse showAvailableRooms(String id,State s,String prefix) {
        s.roomOptions=booking.availableRooms(s.start,s.end,s.attendees);
        s.step=Step.ROOM;
        if(s.roomOptions.isEmpty())return answer(id,prefix+"\nHiện không có phòng nào đủ sức chứa và còn trống trong khung giờ này. Nhập 'làm lại' để chọn thời gian khác.",s.step,null,List.of());
        String list=s.roomOptions.stream().map(r->r.id()+" - "+r.name()+" ("+r.capacity()+" người, "+Objects.toString(r.location(),"chưa có vị trí")+")").reduce((a,b)->a+"\n"+b).orElse("");
        return answer(id,prefix+"\nCác phòng phù hợp:\n"+list+"\nHãy chọn một phòng hoặc nhập 'tự chọn' để hệ thống chọn phòng nhỏ nhất phù hợp.",s.step,null,s.roomOptions);
    }
    private void validateDraft(State s) {
        if(s.start!=null&&!s.start.isAfter(LocalDateTime.now()))throw bad("Thời gian bắt đầu phải ở tương lai.");
        if(s.start!=null&&s.end!=null&&!s.end.isAfter(s.start))throw bad("Thời gian kết thúc phải sau thời gian bắt đầu.");
        if(s.attendees<0)throw bad("Số người phải lớn hơn 0.");
        validateParticipants(s);
    }
    private void validateParticipants(State s) {
        for(String userId:s.participantIds) {
            User u=users.findById(userId).orElseThrow(()->bad("Không tìm thấy người tham dự '"+userId+"'. Hãy nhập lại danh sách."));
            if(!"ACTIVE".equalsIgnoreCase(u.status))throw bad("Người tham dự '"+userId+"' đang bị vô hiệu hóa.");
        }
    }
    private static LocalDateTime parseTime(String value) {
        for(DateTimeFormatter f:List.of(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"),DateTimeFormatter.ISO_LOCAL_DATE_TIME)) {
            try {
                return LocalDateTime.parse(value,f);
            }
            catch(DateTimeParseException ignored) {
            }
        }
        try {
            return java.time.OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDateTime();
        }
        catch(DateTimeParseException e) {
            throw bad("Sai định dạng thời gian. Ví dụ đúng: 12/10/2026 14:00.");
        }
    }
    private static String summary(State s) {
        return "Bạn muốn đặt phòng cho '"+s.title+"', từ "+s.start.format(DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy"))+" đến "+s.end.format(DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy"))+", "+s.attendees+" người, phòng "+(s.roomId==null?"do hệ thống tự chọn":s.roomId)+".";
    }
    private static boolean looksNatural(String s) {
        return s.contains(" ")&&s.length()>20;
    }
    private static String blankToNull(String s) {
        return s==null||s.isBlank()?null:s.trim();
    }
    private static boolean isReset(String s) {
        return s.equalsIgnoreCase("làm lại")||s.equalsIgnoreCase("dat lai")||s.equalsIgnoreCase("reset");
    }
    private static boolean isSkip(String s) {
        return s.equalsIgnoreCase("không")||s.equalsIgnoreCase("khong")||s.equalsIgnoreCase("bỏ qua");
    }
    private static boolean isAuto(String s) {
        return s.equalsIgnoreCase("tự chọn")||s.equalsIgnoreCase("tu chon")||s.equalsIgnoreCase("auto");
    }
    private static boolean isConfirm(String s) {
        return s.equalsIgnoreCase("xác nhận")||s.equalsIgnoreCase("xac nhan")||s.equalsIgnoreCase("đồng ý")||s.equalsIgnoreCase("dong y");
    }
    private static US25ChatMessageResponse answer(String id,String text,Step step,US25ChatBookingResponse booking) {
        return answer(id,text,step,booking,List.of());
    }
    private static US25ChatMessageResponse answer(String id,String text,Step step,US25ChatBookingResponse booking,List<US25AvailableRoom> options) {
        return new US25ChatMessageResponse(id,text,step.name(),booking,options);
    }
    private static ResponseStatusException bad(String text) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,text);
    }
}
