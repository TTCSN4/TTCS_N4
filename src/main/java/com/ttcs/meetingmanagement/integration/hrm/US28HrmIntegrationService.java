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
class US28HrmIntegrationService {
    private final US28HrmErpClient client;
    private final US28HrmSyncService syncService;
    private final US28HrmSyncRunRepository runs;
    private final boolean enabled;
    private final String provider;
    US28HrmIntegrationService(US28HrmErpClient client,US28HrmSyncService syncService,US28HrmSyncRunRepository runs,
    @Value("${integration.hrm.enabled:false}") boolean enabled,@Value("${integration.hrm.provider:HRM-ERP}") String provider) {
        this.client=client;
        this.syncService=syncService;
        this.runs=runs;
        this.enabled=enabled;
        this.provider=provider;
    }
    @Scheduled(fixedDelayString="${integration.hrm.sync-interval-ms:900000}",initialDelayString="${integration.hrm.initial-delay-ms:30000}")
    void scheduledSync() {
        if(enabled)pullNow("SCHEDULED");
    }
    US28SyncResult pullNow(String trigger) {
        if(!enabled)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Tích hợp HRM/ERP chưa được bật");
        US28HrmSyncRun run=startRun(provider+":"+trigger);
        try {
            US28SyncRequest snapshot=client.fetchSnapshot();
            US28SyncResult result=syncService.sync(snapshot);
            complete(run,result);
            return result;
        }
        catch(RuntimeException ex) {
            fail(run,ex);
            throw ex;
        }
    }
    US28SyncResult acceptWebhook(US28SyncRequest request) {
        US28HrmSyncRun run=startRun(normalize(request.source())+":WEBHOOK");
        try {
            US28SyncResult result=syncService.sync(request);
            complete(run,result);
            return result;
        }
        catch(RuntimeException ex) {
            fail(run,ex);
            throw ex;
        }
    }
    US28IntegrationStatus status() {
        List<US28HrmSyncRun> recent=runs.findAllByOrderByStartedAtDesc(PageRequest.of(0,50));
        US28HrmSyncRun last=recent.stream().findFirst().orElse(null);
        LocalDateTime success=recent.stream().filter(x->x.status==US28SyncStatus.SUCCESS).map(x->x.finishedAt).filter(Objects::nonNull).findFirst().orElse(null);
        return new US28IntegrationStatus(enabled,provider,last==null?"NEVER_SYNCED":last.status.name(),success,last==null?null:last.startedAt,last==null?null:last.errorMessage);
    }
    List<US28HrmSyncRun> history(int limit) {
        return runs.findAllByOrderByStartedAtDesc(PageRequest.of(0,Math.max(1,Math.min(limit,100))));
    }
    private US28HrmSyncRun startRun(String source) {
        US28HrmSyncRun run=new US28HrmSyncRun();
        run.source=source;
        run.status=US28SyncStatus.RUNNING;
        run.startedAt=LocalDateTime.now();
        return runs.saveAndFlush(run);
    }
    private void complete(US28HrmSyncRun run,US28SyncResult r) {
        run.status=US28SyncStatus.SUCCESS;
        run.finishedAt=LocalDateTime.now();
        run.departmentsCreated=r.departmentsCreated();
        run.departmentsUpdated=r.departmentsUpdated();
        run.usersCreated=r.usersCreated();
        run.usersUpdated=r.usersUpdated();
        run.usersDeactivated=r.usersDeactivated();
        runs.save(run);
    }
    private void fail(US28HrmSyncRun run,RuntimeException ex) {
        run.status=US28SyncStatus.FAILED;
        run.finishedAt=LocalDateTime.now();
        String value=Objects.toString(ex.getMessage(),ex.getClass().getSimpleName());
        run.errorMessage=value.substring(0,Math.min(1000,value.length()));
        runs.save(run);
    }
    private static String normalize(String value) {
        return value==null||value.isBlank()?"HRM-ERP":value.trim().toUpperCase(Locale.ROOT);
    }
}
