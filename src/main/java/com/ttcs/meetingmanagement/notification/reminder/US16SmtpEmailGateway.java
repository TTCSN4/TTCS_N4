package com.ttcs.meetingmanagement.notification.reminder;
import com.ttcs.meetingmanagement.model.Meeting;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
@Service
class US16SmtpEmailGateway implements US16EmailGateway {
    private final JavaMailSender sender; private final String from;
    US16SmtpEmailGateway(JavaMailSender sender,@Value("${app.mail.from:no-reply@meeting.local}") String from){this.sender=sender;this.from=from;}
    public void send(US16NotificationType type,Meeting meeting,String recipient){
        var mail=new SimpleMailMessage();mail.setFrom(from);mail.setTo(recipient);
        mail.setSubject(type.name()+": "+meeting.title);
        mail.setText("Cuộc họp: "+meeting.title+"\nBắt đầu: "+meeting.startTime+"\nKết thúc: "+meeting.endTime
            +"\nPhòng: "+meeting.roomId+"\nNgười tổ chức: "+meeting.organizerId+"\nNội dung: "+java.util.Objects.toString(meeting.description,""));
        sender.send(mail);
    }
}
