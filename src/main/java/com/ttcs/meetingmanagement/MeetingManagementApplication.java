package com.ttcs.meetingmanagement;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.*;

@SpringBootApplication
@EnableScheduling
public class MeetingManagementApplication {
  public static void main(String[] args){SpringApplication.run(MeetingManagementApplication.class,args);}
  @Bean RestClient.Builder restClientBuilder(){return RestClient.builder();}
}

@JsonAutoDetect(fieldVisibility=JsonAutoDetect.Visibility.ANY)
@Entity @Table(name="roles")
class Role {
  @Id @Column(name="role_id",length=36) String id;
  @Column(name="role_name",nullable=false,unique=true) String name;
  protected Role(){} Role(String id,String name){this.id=id;this.name=name;}
}

@JsonAutoDetect(fieldVisibility=JsonAutoDetect.Visibility.ANY)
@Entity @Table(name="departments",uniqueConstraints=@UniqueConstraint(name="uk_department_hrm_code",columnNames="hrm_code"))
class Department {
  @Id @Column(name="department_id",length=36) String id;
  @Column(name="department_name",nullable=false) String name;
  @Column(name="hrm_code",nullable=false,length=100) String hrmCode;
  @Column(name="last_synced_at",nullable=false) OffsetDateTime lastSyncedAt;
  @PrePersist void create(){if(id==null)id=UUID.randomUUID().toString();}
}

@JsonAutoDetect(fieldVisibility=JsonAutoDetect.Visibility.ANY)
@Entity @Table(name="users",uniqueConstraints={@UniqueConstraint(name="uk_user_hrm_code",columnNames="hrm_code"),@UniqueConstraint(name="uk_user_email",columnNames="email")})
class User {
  @Id @Column(name="user_id",length=36) String id;
  @Column(name="role_id",nullable=false,length=36) String roleId;
  @Column(name="department_id",length=36) String departmentId;
  @Column(name="hrm_code",length=100) String hrmCode;
  @Column(name="full_name",nullable=false) String fullName;
  @Column(nullable=false) String email;
  String phone;
  @Column(nullable=false,length=30) String status;
  @Column(name="password_hash") String passwordHash;
  @Column(name="created_at",nullable=false) OffsetDateTime createdAt;
  @Column(name="updated_at",nullable=false) OffsetDateTime updatedAt;
  @Column(name="last_synced_at") OffsetDateTime lastSyncedAt;
  @PrePersist void create(){if(id==null)id=UUID.randomUUID().toString();createdAt=updatedAt=OffsetDateTime.now();}
  @PreUpdate void update(){updatedAt=OffsetDateTime.now();}
}

enum SyncStatus { RUNNING, SUCCESS, FAILED }
@JsonAutoDetect(fieldVisibility=JsonAutoDetect.Visibility.ANY)
@Entity @Table(name="hrm_sync_runs")
class HrmSyncRun {
  @Id @Column(name="sync_run_id",length=36) String id;
  @Column(nullable=false,length=30) String source;
  @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) SyncStatus status;
  @Column(name="started_at",nullable=false) OffsetDateTime startedAt;
  @Column(name="finished_at") OffsetDateTime finishedAt;
  @Column(name="departments_created",nullable=false) int departmentsCreated;
  @Column(name="departments_updated",nullable=false) int departmentsUpdated;
  @Column(name="users_created",nullable=false) int usersCreated;
  @Column(name="users_updated",nullable=false) int usersUpdated;
  @Column(name="users_deactivated",nullable=false) int usersDeactivated;
  @Column(name="error_message",length=1000) String errorMessage;
  @PrePersist void create(){if(id==null)id=UUID.randomUUID().toString();}
}

interface RoleRepository extends JpaRepository<Role,String>{}
interface DepartmentRepository extends JpaRepository<Department,String>{Optional<Department> findByHrmCode(String code);}
interface UserRepository extends JpaRepository<User,String>{
  Optional<User> findByHrmCode(String code); Optional<User> findByEmailIgnoreCase(String email); List<User> findByHrmCodeIsNotNull();
}
interface HrmSyncRunRepository extends JpaRepository<HrmSyncRun,String>{List<HrmSyncRun> findAllByOrderByStartedAtDesc(PageRequest page);}

record DepartmentPayload(@NotBlank String hrmCode,@NotBlank String name){}
record EmployeePayload(@NotBlank String hrmCode,@NotBlank String fullName,@Email @NotBlank String email,String phone,
                       @NotBlank String departmentHrmCode,String status,String roleCode){
  EmployeePayload(String hrmCode,String fullName,String email,String phone,String departmentHrmCode,String status){this(hrmCode,fullName,email,phone,departmentHrmCode,status,null);}
}
record SyncRequest(@NotEmpty List<@Valid DepartmentPayload> departments,@NotEmpty List<@Valid EmployeePayload> employees,
                   Boolean fullSnapshot,String source,OffsetDateTime sourceUpdatedAt){
  SyncRequest(List<DepartmentPayload> departments,List<EmployeePayload> employees){this(departments,employees,true,"HRM",null);}
}
record SyncResult(int departmentsCreated,int departmentsUpdated,int usersCreated,int usersUpdated,int usersDeactivated,
                  OffsetDateTime syncedAt,String source){
  SyncResult(int dc,int du,int uc,int uu,OffsetDateTime at){this(dc,du,uc,uu,0,at,"HRM");}
}
record IntegrationStatus(boolean enabled,String provider,String state,OffsetDateTime lastSuccessfulSync,
                         OffsetDateTime lastAttempt,String lastError){}
record UserView(String id,String roleId,String departmentId,String hrmCode,String fullName,String email,String phone,
                String status,OffsetDateTime createdAt,OffsetDateTime updatedAt,OffsetDateTime lastSyncedAt){
  static UserView from(User user){return new UserView(user.id,user.roleId,user.departmentId,user.hrmCode,user.fullName,user.email,user.phone,user.status,user.createdAt,user.updatedAt,user.lastSyncedAt);}
}

@Service
class HrmSyncService {
  private static final Set<String> USER_STATUSES=Set.of("ACTIVE","INACTIVE","SUSPENDED","TERMINATED");
  private final DepartmentRepository departments; private final UserRepository users; private final RoleRepository roles;
  HrmSyncService(DepartmentRepository d,UserRepository u,RoleRepository r){departments=d;users=u;roles=r;}

  @Transactional SyncResult sync(SyncRequest request){
    OffsetDateTime now=OffsetDateTime.now();int dc=0,du=0,uc=0,uu=0,deactivated=0;
    validateUniqueCodes(request);Map<String,Department> departmentMap=new HashMap<>();
    for(DepartmentPayload item:request.departments()){
      String code=normalizeCode(item.hrmCode());Optional<Department> existing=departments.findByHrmCode(code);
      Department department=existing.orElseGet(Department::new);department.hrmCode=code;department.name=item.name().trim();department.lastSyncedAt=now;
      departments.save(department);departmentMap.put(code,department);if(existing.isEmpty())dc++;else du++;
    }
    Set<String> receivedEmployees=new HashSet<>();
    for(EmployeePayload item:request.employees()){
      String employeeCode=normalizeCode(item.hrmCode());String departmentCode=normalizeCode(item.departmentHrmCode());receivedEmployees.add(employeeCode);
      Department department=Optional.ofNullable(departmentMap.get(departmentCode)).orElseGet(()->departments.findByHrmCode(departmentCode).orElseThrow(()->badRequest("Không tìm thấy phòng ban HRM/ERP: "+departmentCode)));
      Optional<User> byHrm=users.findByHrmCode(employeeCode);String email=item.email().trim().toLowerCase(Locale.ROOT);Optional<User> byEmail=users.findByEmailIgnoreCase(email);
      if(byEmail.isPresent()&&(byHrm.isEmpty()||!byEmail.get().id.equals(byHrm.get().id)))throw conflict("Email thuộc người dùng khác: "+email);
      String role=normalizeRole(item.roleCode());if(!roles.existsById(role))throw badRequest("Vai trò từ HRM/ERP chưa được ánh xạ: "+role);
      String status=normalizeStatus(item.status());Optional<User> existing=byHrm;User user=existing.orElseGet(User::new);
      user.hrmCode=employeeCode;user.fullName=item.fullName().trim();user.email=email;user.phone=trimToNull(item.phone());user.departmentId=department.id;user.roleId=role;user.status=status;user.lastSyncedAt=now;
      users.save(user);if(existing.isEmpty())uc++;else uu++;
    }
    if(Boolean.TRUE.equals(request.fullSnapshot())){
      for(User user:users.findByHrmCodeIsNotNull())if(!receivedEmployees.contains(user.hrmCode)&&!"INACTIVE".equals(user.status)){user.status="INACTIVE";user.lastSyncedAt=now;deactivated++;}
    }
    return new SyncResult(dc,du,uc,uu,deactivated,now,normalizeSource(request.source()));
  }

  private static void validateUniqueCodes(SyncRequest request){
    Set<String> departments=new HashSet<>();for(DepartmentPayload x:request.departments())if(!departments.add(normalizeCode(x.hrmCode())))throw badRequest("Trùng mã phòng ban trong snapshot: "+x.hrmCode());
    Set<String> employees=new HashSet<>();for(EmployeePayload x:request.employees())if(!employees.add(normalizeCode(x.hrmCode())))throw badRequest("Trùng mã nhân viên trong snapshot: "+x.hrmCode());
  }
  private static String normalizeRole(String role){return role==null||role.isBlank()?"ATTENDEE":role.trim().toUpperCase(Locale.ROOT);}
  private static String normalizeStatus(String status){String value=status==null||status.isBlank()?"ACTIVE":status.trim().toUpperCase(Locale.ROOT);if(!USER_STATUSES.contains(value))throw badRequest("Trạng thái nhân viên không hợp lệ: "+value);return value;}
  private static String normalizeCode(String value){return value.trim().toUpperCase(Locale.ROOT);}
  private static String normalizeSource(String value){return value==null||value.isBlank()?"HRM":value.trim().toUpperCase(Locale.ROOT);}
  private static String trimToNull(String value){return value==null||value.isBlank()?null:value.trim();}
  private static ResponseStatusException badRequest(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
  private static ResponseStatusException conflict(String message){return new ResponseStatusException(HttpStatus.CONFLICT,message);}
}

@Service
class HrmErpClient {
  private final RestClient client;private final String snapshotPath,apiKey;
  HrmErpClient(RestClient.Builder builder,@Value("${integration.hrm.base-url:http://localhost:8098}") String baseUrl,
               @Value("${integration.hrm.snapshot-path:/api/v1/workforce/snapshot}") String snapshotPath,
               @Value("${integration.hrm.api-key:}") String apiKey,
               @Value("${integration.hrm.connect-timeout-ms:5000}") int connectTimeoutMs,
               @Value("${integration.hrm.read-timeout-ms:30000}") int readTimeoutMs){
    if(connectTimeoutMs<=0||readTimeoutMs<=0)throw new IllegalArgumentException("Timeout HRM/ERP phải lớn hơn 0");
    SimpleClientHttpRequestFactory requestFactory=new SimpleClientHttpRequestFactory();requestFactory.setConnectTimeout(connectTimeoutMs);requestFactory.setReadTimeout(readTimeoutMs);
    this.client=builder.requestFactory(requestFactory).baseUrl(baseUrl).build();this.snapshotPath=snapshotPath;this.apiKey=apiKey;
  }
  SyncRequest fetchSnapshot(){
    SyncRequest result=client.get().uri(snapshotPath).headers(h->{if(!apiKey.isBlank())h.set("X-API-Key",apiKey);}).retrieve().body(SyncRequest.class);
    if(result==null)throw new IllegalStateException("HRM/ERP trả về snapshot rỗng");return result;
  }
}

@Service
class HrmIntegrationService {
  private final HrmErpClient client;private final HrmSyncService syncService;private final HrmSyncRunRepository runs;
  private final boolean enabled;private final String provider;
  HrmIntegrationService(HrmErpClient client,HrmSyncService syncService,HrmSyncRunRepository runs,
                        @Value("${integration.hrm.enabled:false}") boolean enabled,@Value("${integration.hrm.provider:HRM-ERP}") String provider){this.client=client;this.syncService=syncService;this.runs=runs;this.enabled=enabled;this.provider=provider;}

  @Scheduled(fixedDelayString="${integration.hrm.sync-interval-ms:900000}",initialDelayString="${integration.hrm.initial-delay-ms:30000}")
  void scheduledSync(){if(enabled)pullNow("SCHEDULED");}

  SyncResult pullNow(String trigger){
    if(!enabled)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Tích hợp HRM/ERP chưa được bật");
    HrmSyncRun run=startRun(provider+":"+trigger);
    try{SyncRequest snapshot=client.fetchSnapshot();SyncResult result=syncService.sync(snapshot);complete(run,result);return result;}
    catch(RuntimeException ex){fail(run,ex);throw ex;}
  }
  SyncResult acceptWebhook(SyncRequest request){HrmSyncRun run=startRun(normalize(request.source())+":WEBHOOK");try{SyncResult result=syncService.sync(request);complete(run,result);return result;}catch(RuntimeException ex){fail(run,ex);throw ex;}}
  IntegrationStatus status(){List<HrmSyncRun> recent=runs.findAllByOrderByStartedAtDesc(PageRequest.of(0,50));HrmSyncRun last=recent.stream().findFirst().orElse(null);OffsetDateTime success=recent.stream().filter(x->x.status==SyncStatus.SUCCESS).map(x->x.finishedAt).filter(Objects::nonNull).findFirst().orElse(null);return new IntegrationStatus(enabled,provider,last==null?"NEVER_SYNCED":last.status.name(),success,last==null?null:last.startedAt,last==null?null:last.errorMessage);}
  List<HrmSyncRun> history(int limit){return runs.findAllByOrderByStartedAtDesc(PageRequest.of(0,Math.max(1,Math.min(limit,100))));}

  private HrmSyncRun startRun(String source){HrmSyncRun run=new HrmSyncRun();run.source=source;run.status=SyncStatus.RUNNING;run.startedAt=OffsetDateTime.now();return runs.saveAndFlush(run);}
  private void complete(HrmSyncRun run,SyncResult r){run.status=SyncStatus.SUCCESS;run.finishedAt=OffsetDateTime.now();run.departmentsCreated=r.departmentsCreated();run.departmentsUpdated=r.departmentsUpdated();run.usersCreated=r.usersCreated();run.usersUpdated=r.usersUpdated();run.usersDeactivated=r.usersDeactivated();runs.save(run);}
  private void fail(HrmSyncRun run,RuntimeException ex){run.status=SyncStatus.FAILED;run.finishedAt=OffsetDateTime.now();String value=Objects.toString(ex.getMessage(),ex.getClass().getSimpleName());run.errorMessage=value.substring(0,Math.min(1000,value.length()));runs.save(run);}
  private static String normalize(String value){return value==null||value.isBlank()?"HRM-ERP":value.trim().toUpperCase(Locale.ROOT);}
}

@RestController @RequestMapping("/api/integrations/hrm")
class HrmController {
  private final HrmSyncService syncService;private final HrmIntegrationService integration;private final UserRepository users;private final DepartmentRepository departments;private final String integrationKey;
  HrmController(HrmSyncService syncService,HrmIntegrationService integration,UserRepository users,DepartmentRepository departments,@Value("${integration.hrm.webhook-key:}") String integrationKey){this.syncService=syncService;this.integration=integration;this.users=users;this.departments=departments;this.integrationKey=integrationKey;}
  @PostMapping("/pull") ResponseEntity<SyncResult> pull(){return ResponseEntity.ok(integration.pullNow("MANUAL"));}
  @PostMapping("/webhook") ResponseEntity<SyncResult> webhook(@RequestHeader(value="X-Integration-Key",required=false) String key,@Valid @RequestBody SyncRequest request){authorize(key);return ResponseEntity.ok(integration.acceptWebhook(request));}
  @PostMapping("/sync") ResponseEntity<SyncResult> sync(@RequestHeader(value="X-Integration-Key",required=false) String key,@Valid @RequestBody SyncRequest request){authorize(key);return ResponseEntity.ok(syncService.sync(request));}
  @GetMapping("/status") ResponseEntity<IntegrationStatus> status(){return ResponseEntity.ok(integration.status());}
  @GetMapping("/history") ResponseEntity<List<HrmSyncRun>> history(@RequestParam(defaultValue="20") int limit){return ResponseEntity.ok(integration.history(limit));}
  @GetMapping("/users") ResponseEntity<List<UserView>> users(){return ResponseEntity.ok(users.findAll().stream().map(UserView::from).toList());}
  @GetMapping("/departments") ResponseEntity<List<Department>> departments(){return ResponseEntity.ok(departments.findAll());}
  private void authorize(String supplied){
    if(integrationKey.isBlank())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Chưa cấu hình khóa tích hợp HRM/ERP");
    if(supplied==null||!MessageDigest.isEqual(integrationKey.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8)))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Khóa tích hợp không hợp lệ");
  }
}

@RestControllerAdvice
class GlobalExceptionHandler {
  @ExceptionHandler(ResponseStatusException.class) ResponseEntity<Map<String,Object>> responseStatus(ResponseStatusException ex){return error(ex.getStatusCode(),Objects.toString(ex.getReason(),"Yêu cầu không hợp lệ"));}
  @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<Map<String,Object>> validation(MethodArgumentNotValidException ex){Map<String,String> fields=new LinkedHashMap<>();ex.getBindingResult().getFieldErrors().forEach(e->fields.put(e.getField(),e.getDefaultMessage()));Map<String,Object> body=new LinkedHashMap<>();body.put("status",400);body.put("error","Bad Request");body.put("message",fields.values().stream().findFirst().orElse("Dữ liệu không hợp lệ"));body.put("errors",fields);return ResponseEntity.badRequest().body(body);}
  @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<Map<String,Object>> integrity(DataIntegrityViolationException ex){return error(HttpStatus.CONFLICT,"Dữ liệu HRM/ERP bị trùng hoặc vi phạm ràng buộc");}
  private static ResponseEntity<Map<String,Object>> error(HttpStatusCode status,String message){Map<String,Object> body=new LinkedHashMap<>();body.put("status",status.value());body.put("error",status.toString());body.put("message",message);body.put("timestamp",OffsetDateTime.now());return ResponseEntity.status(status).body(body);}
}

@org.springframework.context.annotation.Configuration
class SeedData {
  @Bean ApplicationRunner seed(RoleRepository roles){return args->{if(!roles.existsById("ATTENDEE"))roles.save(new Role("ATTENDEE","Người tham dự"));if(!roles.existsById("ORGANIZER"))roles.save(new Role("ORGANIZER","Người tổ chức"));if(!roles.existsById("ADMIN"))roles.save(new Role("ADMIN","Quản trị viên"));};}
}
