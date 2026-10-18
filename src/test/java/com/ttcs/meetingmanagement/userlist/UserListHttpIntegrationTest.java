package com.ttcs.meetingmanagement.userlist;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.*;
import com.ttcs.meetingmanagement.config.RbacSecurityConfig;
import com.ttcs.meetingmanagement.exception.GlobalExceptionHandler;
import com.ttcs.meetingmanagement.model.*;
import com.ttcs.meetingmanagement.rbac.RbacAccess;
import com.ttcs.meetingmanagement.repository.RoleRepository;
import com.ttcs.meetingmanagement.repository.UserRepository;
import com.ttcs.meetingmanagement.security.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.*;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes=UserListHttpIntegrationTest.App.class,properties={
 "spring.datasource.url=jdbc:h2:mem:us19_http;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","spring.jpa.hibernate.ddl-auto=create-drop",
 "app.mobile.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=","app.mobile.jwt.issuer=us19-tests","app.mobile.jwt.audience=us19-api"})
@AutoConfigureMockMvc class UserListHttpIntegrationTest {
 static final byte[] KEY="0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
 @Autowired MockMvc mvc; @Autowired UserRepository users; @Autowired RoleRepository roles;
 @BeforeEach void fixture(){users.deleteAll();roles.deleteAll(); Role a=role("R_ADMIN","Admin"),m=role("R_MEMBER","Member"); roles.saveAll(List.of(a,m)); users.saveAll(List.of(user("ADMIN","System Admin","admin@example.com","ACTIVE",a,1),user("MEMBER","Member User","member@example.com","ACTIVE",m,2),user("DISABLED","Disabled Admin","disabled@example.com","INACTIVE",a,3),user("U4","Nguyễn Văn A","a%_!@example.com","ACTIVE",m,4),user("U5","nguyễn văn B","b@example.com","ACTIVE",m,5)));}
 @Test void adminGetsListAndNoPassword() throws Exception {mvc.perform(get("/api/admin/users").param("search"," NGUYỄN ").param("status"," active ").param("roleId","R_MEMBER").param("size","1").header(HttpHeaders.AUTHORIZATION,bearer("ADMIN",null))).andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1)).andExpect(jsonPath("$.content[0].userId").value("U5")).andExpect(jsonPath("$.content[0].passwordHash").doesNotExist()).andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.totalPages").value(2));}
 @Test void adminGetsDetailAndServiceStatuses() throws Exception {mvc.perform(get("/api/admin/users/U4").header(HttpHeaders.AUTHORIZATION,bearer("ADMIN",null))).andExpect(status().isOk()).andExpect(jsonPath("$.email").value("a%_!@example.com")).andExpect(jsonPath("$.passwordHash").doesNotExist());mvc.perform(get("/api/admin/users/missing").header(HttpHeaders.AUTHORIZATION,bearer("ADMIN",null))).andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));mvc.perform(get("/api/admin/users").param("size","101").header(HttpHeaders.AUTHORIZATION,bearer("ADMIN",null))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));}
 @Test void rejectsUnauthorizedAndForbidden() throws Exception {mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());mvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION,"Bearer invalid")).andExpect(status().isUnauthorized());mvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION,bearer("MEMBER",null))).andExpect(status().isForbidden());mvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION,bearer("DISABLED",null))).andExpect(status().isForbidden());mvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION,bearer("MEMBER","Admin"))).andExpect(status().isForbidden());}
 static Role role(String id,String n){Role r=new Role();r.roleId=id;r.roleName=n;return r;} static User user(String id,String n,String e,String s,Role r,int d){User u=new User();u.userId=id;u.fullName=n;u.email=e;u.phone="090"+d;u.status=s;u.role=r;u.departmentId="D1";u.createdAt=LocalDateTime.of(2026,1,d,8,0);u.passwordHash="secret";return u;}
 static String bearer(String sub,String role)throws Exception{JWTClaimsSet.Builder c=new JWTClaimsSet.Builder().subject(sub).issuer("us19-tests").audience("us19-api").issueTime(new Date()).expirationTime(new Date(System.currentTimeMillis()+60000));if(role!=null)c.claim("role",role);SignedJWT j=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).build(),c.build());j.sign(new MACSigner(KEY));return "Bearer "+j.serialize();}
 @SpringBootConfiguration @EnableAutoConfiguration @EntityScan(basePackages="com.ttcs.meetingmanagement") @EnableJpaRepositories(basePackageClasses=UserRepository.class) @Import({UserListController.class,UserListService.class,UserListRepository.class,RbacAccess.class,RbacSecurityConfig.class,MobileSecurityConfig.class,MobileJwtAuthenticationConverter.class,GlobalExceptionHandler.class}) static class App{}
}
