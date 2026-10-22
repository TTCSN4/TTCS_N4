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
@JsonAutoDetect(fieldVisibility=JsonAutoDetect.Visibility.ANY)
@Entity @Table(name="hrm_sync_runs")
class US28HrmSyncRun {
    @Id @Column(name="sync_run_id",length=36) String id;
    @Column(nullable=false,length=30) String source;
    @Enumerated(EnumType.STRING) @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    @Column(nullable=false,length=20) US28SyncStatus status;
    @Column(name="started_at",nullable=false) LocalDateTime startedAt;
    @Column(name="finished_at") LocalDateTime finishedAt;
    @Column(name="departments_created",nullable=false) int departmentsCreated;
    @Column(name="departments_updated",nullable=false) int departmentsUpdated;
    @Column(name="users_created",nullable=false) int usersCreated;
    @Column(name="users_updated",nullable=false) int usersUpdated;
    @Column(name="users_deactivated",nullable=false) int usersDeactivated;
    @Column(name="error_message",length=1000) String errorMessage;
    @PrePersist void create() {
        if(id==null)id=UUID.randomUUID().toString();
    }
}
