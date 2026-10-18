package com.ttcs.meetingmanagement.userlist;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.*;
import com.ttcs.meetingmanagement.config.RbacSecurityConfig;
import com.ttcs.meetingmanagement.model.*;
import com.ttcs.meetingmanagement.rbac.RbacAccess;
import com.ttcs.meetingmanagement.repository.*;
import com.ttcs.meetingmanagement.security.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes=SharedSecurityRegressionHttpIntegrationTest.App.class,properties={
 "spring.datasource.url=jdbc:h2:mem:shared_security;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","spring.jpa.hibernate.ddl-auto=create-drop",
 "app.mobile.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=","app.mobile.jwt.issuer=security-tests","app.mobile.jwt.audience=security-api"})
@AutoConfigureMockMvc class SharedSecurityRegressionHttpIntegrationTest {
 static final byte[] KEY="0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
 @Autowired MockMvc mvc; @Autowired UserRepository users; @Autowired RoleRepository roles;
 @BeforeEach void fixture(){users.deleteAll();roles.deleteAll();Role admin=role("R_ADMIN","Admin"),booker=role("R_BOOKER","Người đặt lịch"),attendee=role("R_ATTENDEE","Người tham dự"),member=role("R_MEMBER","Member");roles.saveAll(List.of(admin,booker,attendee,member));users.saveAll(List.of(user("ADMIN",admin),user("BOOKER",booker),user("ATTENDEE",attendee),user("MEMBER",member)));}
 @Test void preservesAdminBookingRoomAndDenyAllRules() throws Exception {
  mvc.perform(get("/api/admin/security-check").header(HttpHeaders.AUTHORIZATION,bearer("ADMIN"))).andExpect(status().isOk());
  mvc.perform(get("/api/admin/security-check").header(HttpHeaders.AUTHORIZATION,bearer("BOOKER"))).andExpect(status().isForbidden());
  mvc.perform(post("/api/meetings").header(HttpHeaders.AUTHORIZATION,bearer("BOOKER"))).andExpect(status().isOk());
  mvc.perform(post("/api/room-bookings").header(HttpHeaders.AUTHORIZATION,bearer("BOOKER"))).andExpect(status().isOk());
  mvc.perform(post("/api/meetings/suggestions").header(HttpHeaders.AUTHORIZATION,bearer("ATTENDEE"))).andExpect(status().isForbidden());
  mvc.perform(get("/api/rooms").header(HttpHeaders.AUTHORIZATION,bearer("ATTENDEE"))).andExpect(status().isOk());
  mvc.perform(get("/api/rooms").header(HttpHeaders.AUTHORIZATION,bearer("MEMBER"))).andExpect(status().isForbidden());
  mvc.perform(get("/api/not-allowed").header(HttpHeaders.AUTHORIZATION,bearer("ADMIN"))).andExpect(status().isForbidden());
 }
 @Test void keepsMobileCalendarInItsOwnAuthenticatedJwtChain() throws Exception {
  mvc.perform(get("/api/mobile/calendar")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/mobile/calendar").header(HttpHeaders.AUTHORIZATION,bearer("MEMBER"))).andExpect(status().isOk());
 }
 @Test void disablesCsrfOnlyForStatelessApiAndKeepsItForNonApiRoutes() throws Exception {
  mvc.perform(post("/api/meetings").header(HttpHeaders.AUTHORIZATION,bearer("ADMIN"))).andExpect(status().isOk());
  mvc.perform(post("/outside-api")).andExpect(status().isForbidden());
 }
 static Role role(String id,String name){Role r=new Role();r.roleId=id;r.roleName=name;return r;}
 static User user(String id,Role role){User u=new User();u.userId=id;u.fullName=id;u.email=id.toLowerCase()+"@example.com";u.status="ACTIVE";u.role=role;u.passwordHash="secret";return u;}
 static String bearer(String sub)throws Exception{JWTClaimsSet c=new JWTClaimsSet.Builder().subject(sub).issuer("security-tests").audience("security-api").issueTime(new Date()).expirationTime(new Date(System.currentTimeMillis()+60000)).build();SignedJWT j=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).build(),c);j.sign(new MACSigner(KEY));return "Bearer "+j.serialize();}
 @RestController static class Endpoints {
  @PostMapping({"/api/meetings","/api/room-bookings","/api/meetings/suggestions","/outside-api"}) String post(){return "ok";}
  @GetMapping({"/api/admin/security-check","/api/rooms","/api/mobile/calendar"}) String get(){return "ok";}
 }
 @SpringBootConfiguration @EnableAutoConfiguration @EntityScan(basePackages="com.ttcs.meetingmanagement") @EnableJpaRepositories(basePackageClasses=UserRepository.class) @Import({Endpoints.class,RbacAccess.class,RbacSecurityConfig.class,MobileSecurityConfig.class,MobileJwtAuthenticationConverter.class}) static class App{}
}
