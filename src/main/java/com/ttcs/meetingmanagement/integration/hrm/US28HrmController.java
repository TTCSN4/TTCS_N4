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
@RestController @RequestMapping("/api/integrations/hrm")
class US28HrmController {
    private final US28HrmSyncService syncService;
    private final US28HrmIntegrationService integration;
    private final UserRepository users;
    private final DepartmentRepository departments;
    private final String integrationKey;
    US28HrmController(US28HrmSyncService syncService,US28HrmIntegrationService integration,UserRepository users,DepartmentRepository departments,@Value("${integration.hrm.webhook-key:}") String integrationKey) {
        this.syncService=syncService;
        this.integration=integration;
        this.users=users;
        this.departments=departments;
        this.integrationKey=integrationKey;
    }
    @PostMapping("/pull") ResponseEntity<US28SyncResult> pull() {
        return ResponseEntity.ok(integration.pullNow("MANUAL"));
    }
    @PostMapping("/webhook") ResponseEntity<US28SyncResult> webhook(@RequestHeader(value="X-Integration-Key",required=false) String key,@Valid @RequestBody US28SyncRequest request) {
        authorize(key);
        return ResponseEntity.ok(integration.acceptWebhook(request));
    }
    @PostMapping("/sync") ResponseEntity<US28SyncResult> sync(@RequestHeader(value="X-Integration-Key",required=false) String key,@Valid @RequestBody US28SyncRequest request) {
        authorize(key);
        return ResponseEntity.ok(syncService.sync(request));
    }
    @GetMapping("/status") ResponseEntity<US28IntegrationStatus> status() {
        return ResponseEntity.ok(integration.status());
    }
    @GetMapping("/history") ResponseEntity<List<US28HrmSyncRun>> history(@RequestParam(defaultValue="20") int limit) {
        return ResponseEntity.ok(integration.history(limit));
    }
    @GetMapping("/users") ResponseEntity<List<US28UserView>> users() {
        return ResponseEntity.ok(users.findAll().stream().map(US28UserView::from).toList());
    }
    @GetMapping("/departments") ResponseEntity<List<Department>> departments() {
        return ResponseEntity.ok(departments.findAll());
    }
    private void authorize(String supplied) {
        if(integrationKey.isBlank())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Chưa cấu hình khóa tích hợp HRM/ERP");
        if(supplied==null||!MessageDigest.isEqual(integrationKey.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8)))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Khóa tích hợp không hợp lệ");
    }
}
