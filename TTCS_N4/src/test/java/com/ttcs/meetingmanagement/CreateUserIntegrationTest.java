package com.ttcs.meetingmanagement;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest @Transactional
class CreateUserIntegrationTest {
 @Autowired UserService service;@Autowired UserRepository users;
 @Test void persistsAllFormFieldsAndRejectsDuplicate(){
 var r=new CreateUserRequest("Nguyen Minh","test-us18@example.com","0901234567","Aa1!abcd","ADMIN","DEP-IT","minhtest","INACTIVE","Specialist","Account integration");
 var created=service.create(r);users.flush();var stored=users.findById(created.id()).orElseThrow();
 assertEquals("minhtest",created.username());assertEquals("INACTIVE",stored.status);assertEquals("Specialist",stored.position);assertEquals("Account integration",stored.description);
 assertTrue(stored.passwordHash.startsWith("$2"));assertNotEquals(r.password(),stored.passwordHash);
 assertThrows(ResponseStatusException.class,()->service.create(new CreateUserRequest("Another User","another-us18@example.com",null,"Aa1!abcd","ADMIN",null,"minhtest","ACTIVE",null,null)));
 assertThrows(ResponseStatusException.class,()->service.create(new CreateUserRequest("Another User","test-us18@example.com",null,"Aa1!abcd","ADMIN",null,"othername","ACTIVE",null,null)));
 }
}
