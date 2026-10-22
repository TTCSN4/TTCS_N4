package com.ttcs.meetingmanagement.integration.hrm;
import com.ttcs.meetingmanagement.model.*;
import com.ttcs.meetingmanagement.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes=com.ttcs.meetingmanagement.MeetingmanagementApplication.class) @Transactional
class US28HrmSyncIntegrationTest {
    @Autowired US28HrmSyncService service;@Autowired UserRepository users;@Autowired RoleRepository roles;
    @BeforeEach void roles(){var role=new Role();role.roleId="ATTENDEE";role.roleName="Người tham dự";roles.saveAndFlush(role);}
    US28SyncRequest snapshot(boolean full,String...codes){
        var employees=java.util.Arrays.stream(codes).map(code->new US28EmployeePayload(code,"Employee "+code,code.toLowerCase()+"@example.com",null,"IT","ACTIVE","ATTENDEE")).toList();
        return new US28SyncRequest(List.of(new US28DepartmentPayload("it","IT")),employees,full,"HRM",null);
    }
    @Test void repeatedSnapshotUpdatesSameUser(){
        var first=service.sync(snapshot(true,"EMP1"));users.flush();var id=users.findByHrmCode("EMP1").orElseThrow().userId;
        var again=service.sync(snapshot(true,"EMP1"));users.flush();
        assertEquals(1,first.usersCreated());assertEquals(0,again.usersCreated());assertEquals(1,again.usersUpdated());
        assertEquals(id,users.findByHrmCode("EMP1").orElseThrow().userId);
    }
    @Test void onlyCompleteSnapshotsDeactivateMissingEmployees(){
        service.sync(snapshot(true,"EMP1","EMP2"));users.flush();
        service.sync(snapshot(false,"EMP1"));users.flush();assertEquals("ACTIVE",users.findByHrmCode("EMP2").orElseThrow().status);
        assertEquals(1,service.sync(snapshot(true,"EMP1")).usersDeactivated());users.flush();
        assertEquals("INACTIVE",users.findByHrmCode("EMP2").orElseThrow().status);
    }
    @Test void duplicateCodesRejectTheSnapshot(){
        assertEquals(400,assertThrows(ResponseStatusException.class,()->service.sync(snapshot(true,"EMP1","EMP1"))).getStatusCode().value());
        assertTrue(users.findByHrmCode("EMP1").isEmpty());
    }
}
