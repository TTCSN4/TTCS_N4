package com.ttcs.meetingmanagement.service;
import com.ttcs.meetingmanagement.model.*;
import com.ttcs.meetingmanagement.repository.UserRepository;
import com.ttcs.meetingmanagement.room.Room;
import com.ttcs.meetingmanagement.room.RoomRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @Transactional
class BackendRoomPermissionIntegrationTest {
    @Autowired UserRepository users;@Autowired RoomRepository rooms;
    @Autowired RoomBookingPermissionService permissions;@Autowired EntityManager em;
    @BeforeEach void fixture(){
        var user=new User();user.userId="PERMISSION-U";user.fullName="Permission User";user.email="perm@company.com";user.passwordHash="hash";user.departmentId="IT";user.status="ACTIVE";users.save(user);
        var room=new Room();room.roomId="PERMISSION-R";room.roomName="Permission room";room.capacity=6;room.status="AVAILABLE";rooms.saveAndFlush(room);
    }
    @Test void supportsUnrestrictedRoomsAndDepartmentScopes(){
        assertDoesNotThrow(()->permissions.assertCanBook("PERMISSION-U","PERMISSION-R"));
        var restriction=new RoomRestriction();restriction.roomId="PERMISSION-R";restriction.departmentId="IT";em.persist(restriction);em.flush();
        assertDoesNotThrow(()->permissions.assertCanBook("PERMISSION-U","PERMISSION-R"));
    }
    @Test void rejectsUserOutsideTheRoomAllowList(){
        var restriction=new RoomRestriction();restriction.roomId="PERMISSION-R";restriction.userId="OTHER";em.persist(restriction);em.flush();
        assertEquals(403,assertThrows(ResponseStatusException.class,()->permissions.assertCanBook("PERMISSION-U","PERMISSION-R")).getStatusCode().value());
    }
}
