package com.ttcs.meetingmanagement.user.creation;

import com.ttcs.meetingmanagement.model.User;
import com.ttcs.meetingmanagement.model.Role;
import com.ttcs.meetingmanagement.model.Meeting;
import com.ttcs.meetingmanagement.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.net.URI;
import java.net.http.*;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.datasource.url=jdbc:h2:mem:us18http;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop","spring.mail.host=localhost","app.reminder.enabled=false"})
class US18AccountHttpTest {
    @Value("${local.server.port}") int port;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired MeetingRepository meetings;
    @Autowired BCryptPasswordEncoder encoder;
    final HttpClient client=HttpClient.newHttpClient();
    HttpResponse<String> call(String method,String path,String body,String token)throws Exception{
        var b=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).header("Content-Type","application/json");
        if(token!=null)b.header("Authorization","Bearer "+token);
        return client.send(b.method(method,HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    String login(String email)throws Exception{
        var response=call("POST","/api/auth/login","{\"email\":\""+email+"\",\"password\":\"test-password\"}",null);
        assertEquals(200,response.statusCode(),response.body());
        var m=java.util.regex.Pattern.compile("\"accessToken\":\"([^\"]+)\"").matcher(response.body());assertTrue(m.find());return m.group(1);
    }
    User user(String name,String roleName){
        Role r=new Role();r.roleId=UUID.randomUUID().toString();r.roleName=roleName;roles.saveAndFlush(r);
        User u=new User();u.userId=UUID.randomUUID().toString();u.fullName=name;u.email=u.userId+"@test.local";u.role=r;u.status="ACTIVE";u.passwordHash=encoder.encode("test-password");return users.saveAndFlush(u);
    }
    @Test void lockInvalidatesExistingTokenAndPreservesMeeting()throws Exception{
        User admin=user("Admin","Admin"),member=user("Member","Member");
        Meeting meeting=new Meeting();meeting.title="Historical reference";meeting.organizerId=member.userId;meeting.startTime=LocalDateTime.now().plusDays(1);meeting.endTime=meeting.startTime.plusHours(1);meetings.saveAndFlush(meeting);
        String adminToken=login(admin.email),memberToken=login(member.email);
        assertEquals(403,call("POST","/api/users/"+admin.userId+"/lock","",memberToken).statusCode());
        assertEquals(200,call("POST","/api/users/"+member.userId+"/lock","",adminToken).statusCode());
        assertEquals("LOCKED",users.findById(member.userId).orElseThrow().status);
        assertEquals(401,call("POST","/api/auth/login","{\"email\":\""+member.email+"\",\"password\":\"test-password\"}",null).statusCode());
        int blocked=call("GET","/api/meetings","",memberToken).statusCode();assertTrue(blocked==401||blocked==403);
        assertEquals(member.userId,meetings.findById(meeting.meetingId).orElseThrow().organizerId);
        assertEquals(200,call("POST","/api/users/"+member.userId+"/unlock","",adminToken).statusCode());
        assertEquals("ACTIVE",users.findById(member.userId).orElseThrow().status);
        assertEquals(200,call("GET","/api/meetings","",memberToken).statusCode());
    }
}
