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
class US28HrmErpClient {
    private final RestClient client;
    private final String snapshotPath,apiKey;
    US28HrmErpClient(RestClient.Builder builder,@Value("${integration.hrm.base-url:http://localhost:8098}") String baseUrl,
    @Value("${integration.hrm.snapshot-path:/api/v1/workforce/snapshot}") String snapshotPath,
    @Value("${integration.hrm.api-key:}") String apiKey,
    @Value("${integration.hrm.connect-timeout-ms:5000}") int connectTimeoutMs,
    @Value("${integration.hrm.read-timeout-ms:30000}") int readTimeoutMs) {
        if(connectTimeoutMs<=0||readTimeoutMs<=0)throw new IllegalArgumentException("Timeout HRM/ERP phải lớn hơn 0");
        SimpleClientHttpRequestFactory requestFactory=new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        this.client=builder.requestFactory(requestFactory).baseUrl(baseUrl).build();
        this.snapshotPath=snapshotPath;
        this.apiKey=apiKey;
    }
    US28SyncRequest fetchSnapshot() {
        US28SyncRequest result=client.get().uri(snapshotPath).headers(h-> {
            if(!apiKey.isBlank())h.set("X-API-Key",apiKey);
        }
        ).retrieve().body(US28SyncRequest.class);
        if(result==null)throw new IllegalStateException("HRM/ERP trả về snapshot rỗng");
        return result;
    }
}
