package com.ttcs.meetingmanagement.integration.hrm;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
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
import java.time.LocalDateTime;
import java.util.*;
import com.ttcs.meetingmanagement.model.*;
import com.ttcs.meetingmanagement.repository.*;
@Service
class US28HrmSyncService {
    private static final Set<String> USER_STATUSES=Set.of("ACTIVE","INACTIVE","SUSPENDED","TERMINATED");
    private final DepartmentRepository departments;
    private final UserRepository users;
    private final RoleRepository roles;
    private final jakarta.validation.Validator validator;
    US28HrmSyncService(DepartmentRepository d,UserRepository u,RoleRepository r,jakarta.validation.Validator validator) {
        departments=d;
        users=u;
        roles=r;
        this.validator=validator;
    }
    @Transactional US28SyncResult sync(US28SyncRequest request) {
        if(request==null||request.departments()==null||request.employees()==null)throw badRequest("Snapshot departments and employees are required");
        var violations=validator.validate(request);
        if(!violations.isEmpty())throw badRequest(violations.iterator().next().getMessage());
        LocalDateTime now=LocalDateTime.now();
        int dc=0,du=0,uc=0,uu=0,deactivated=0;
        validateUniqueCodes(request);
        Map<String,Department> departmentMap=new HashMap<>();
        for(US28DepartmentPayload item:request.departments()) {
            String code=normalizeCode(item.hrmCode());
            Optional<Department> existing=departments.findByHrmCode(code);
            Department department=existing.orElseGet(Department::new);
            department.hrmCode=code;
            department.name=item.name().trim();
            department.lastSyncedAt=now;
            departments.save(department);
            departmentMap.put(code,department);
            if(existing.isEmpty())dc++;
            else du++;
        }
        Set<String> receivedEmployees=new HashSet<>();
        for(US28EmployeePayload item:request.employees()) {
            String employeeCode=normalizeCode(item.hrmCode());
            String departmentCode=normalizeCode(item.departmentHrmCode());
            receivedEmployees.add(employeeCode);
            Department department=Optional.ofNullable(departmentMap.get(departmentCode)).orElseGet(()->departments.findByHrmCode(departmentCode).orElseThrow(()->badRequest("Không tìm thấy phòng ban HRM/ERP: "+departmentCode)));
            Optional<User> byHrm=users.findByHrmCode(employeeCode);
            String email=item.email().trim().toLowerCase(Locale.ROOT);
            Optional<User> byEmail=users.findByEmailIgnoreCase(email);
            if(byEmail.isPresent()&&(byHrm.isEmpty()||!byEmail.get().userId.equals(byHrm.get().userId)))throw conflict("Email thuộc người dùng khác: "+email);
            String role=normalizeRole(item.roleCode());
            if(!roles.existsById(role))throw badRequest("Vai trò từ HRM/ERP chưa được ánh xạ: "+role);
            String status=normalizeStatus(item.status());
            Optional<User> existing=byHrm;
            User user=existing.orElseGet(User::new);
            user.hrmCode=employeeCode;
            user.fullName=item.fullName().trim();
            user.email=email;
            user.phone=trimToNull(item.phone());
            user.departmentId=department.id;
            user.role=roles.getReferenceById(role);
            user.status=status;
            user.lastSyncedAt=now;
            users.save(user);
            if(existing.isEmpty())uc++;
            else uu++;
        }
        if(Boolean.TRUE.equals(request.fullSnapshot())) {
            for(User user:users.findByHrmCodeIsNotNull())if(!receivedEmployees.contains(user.hrmCode)&&!"INACTIVE".equals(user.status)) {
                user.status="INACTIVE";
                user.lastSyncedAt=now;
                deactivated++;
            }
        }
        return new US28SyncResult(dc,du,uc,uu,deactivated,now,normalizeSource(request.source()));
    }
    private static void validateUniqueCodes(US28SyncRequest request) {
        Set<String> departments=new HashSet<>();
        for(US28DepartmentPayload x:request.departments())if(!departments.add(normalizeCode(x.hrmCode())))throw badRequest("Trùng mã phòng ban trong snapshot: "+x.hrmCode());
        Set<String> employees=new HashSet<>();
        for(US28EmployeePayload x:request.employees())if(!employees.add(normalizeCode(x.hrmCode())))throw badRequest("Trùng mã nhân viên trong snapshot: "+x.hrmCode());
    }
    private static String normalizeRole(String role) {
        return role==null||role.isBlank()?"ATTENDEE":role.trim().toUpperCase(Locale.ROOT);
    }
    private static String normalizeStatus(String status) {
        String value=status==null||status.isBlank()?"ACTIVE":status.trim().toUpperCase(Locale.ROOT);
        if(!USER_STATUSES.contains(value))throw badRequest("Trạng thái nhân viên không hợp lệ: "+value);
        return value;
    }
    private static String normalizeCode(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }
    private static String normalizeSource(String value) {
        return value==null||value.isBlank()?"HRM":value.trim().toUpperCase(Locale.ROOT);
    }
    private static String trimToNull(String value) {
        return value==null||value.isBlank()?null:value.trim();
    }
    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);
    }
    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT,message);
    }
}
