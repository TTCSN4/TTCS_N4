package com.ttcs.meetingmanagement.user.creation;
import com.ttcs.meetingmanagement.model.*;
import com.ttcs.meetingmanagement.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class US18UserServiceTest {
    UserRepository users=mock(UserRepository.class);RoleRepository roles=mock(RoleRepository.class);
    DepartmentRepository departments=mock(DepartmentRepository.class);BCryptPasswordEncoder encoder=new BCryptPasswordEncoder(4);
    US18UserService service=new US18UserService(users,roles,departments,encoder);
    @Test void normalizesEmailAndHashesPasswordWithoutReturningIt(){
        Role role=new Role();role.roleId="ATTENDEE";role.roleName="Người tham dự";
        when(roles.findById("ATTENDEE")).thenReturn(java.util.Optional.of(role));when(roles.getReferenceById("ATTENDEE")).thenReturn(role);
        when(users.save(any(User.class))).thenAnswer(call->{User u=call.getArgument(0);u.userId="U1";
            assertTrue(encoder.matches("safe-password",u.passwordHash));assertEquals("user@example.com",u.email);return u;});
        var result=service.create(new US18CreateUserRequest(" User "," USER@example.com ",null,"safe-password","ATTENDEE",null));
        assertEquals("U1",result.id());assertEquals("user@example.com",result.email());assertEquals("ATTENDEE",result.roleId());
    }
    @Test void rejectsDuplicateEmailBeforeSaving(){
        when(users.existsByEmailIgnoreCase("user@example.com")).thenReturn(true);
        var ex=assertThrows(ResponseStatusException.class,()->service.create(new US18CreateUserRequest("User","user@example.com",null,"safe-password","ATTENDEE",null)));
        assertEquals(409,ex.getStatusCode().value());verify(users,never()).save(any());
    }
    @Test void rejectsInvalidRole(){
        assertEquals(400,assertThrows(ResponseStatusException.class,()->service.create(new US18CreateUserRequest("User","u@example.com",null,"safe-password","NO-ROLE",null))).getStatusCode().value());
    }
}
