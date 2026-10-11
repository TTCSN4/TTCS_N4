package com.ttcs.meetingmanagement;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:us28test;MODE=MySQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "integration.hrm.enabled=false"
})
class HrmSyncServiceTest {
  @Autowired HrmSyncService service;
  @Autowired RoleRepository roles;
  @Autowired UserRepository users;
  @Autowired DepartmentRepository departments;
  @Autowired HrmController controller;

  @BeforeEach
  void resetData() {
    users.deleteAll();
    departments.deleteAll();
    if (!roles.existsById("ATTENDEE")) roles.save(new Role("ATTENDEE", "Nguoi tham du"));
  }

  @Test
  void upsertsSameHrmRecordsWithoutCreatingDuplicates() {
    var request = snapshot("NV1", "a@example.com", "ACTIVE", true);
    var first = service.sync(request);
    var second = service.sync(request);
    assertThat(first.departmentsCreated()).isEqualTo(1);
    assertThat(first.usersCreated()).isEqualTo(1);
    assertThat(second.departmentsUpdated()).isEqualTo(1);
    assertThat(second.usersUpdated()).isEqualTo(1);
    assertThat(users.count()).isEqualTo(1);
  }

  @Test
  void deactivatesEmployeeMissingFromAFullSnapshot() {
    service.sync(snapshot("NV1", "a@example.com", "ACTIVE", true));
    var result = service.sync(snapshot("NV2", "b@example.com", "ACTIVE", true));
    assertThat(result.usersDeactivated()).isEqualTo(1);
    assertThat(users.findByHrmCode("NV1").orElseThrow().status).isEqualTo("INACTIVE");
    assertThat(users.findByHrmCode("NV2").orElseThrow().status).isEqualTo("ACTIVE");
  }

  @Test
  void keepsMissingEmployeeActiveForIncrementalWebhook() {
    service.sync(snapshot("NV1", "a@example.com", "ACTIVE", true));
    service.sync(snapshot("NV2", "b@example.com", "ACTIVE", false));
    assertThat(users.findByHrmCode("NV1").orElseThrow().status).isEqualTo("ACTIVE");
  }

  @Test
  void rejectsUnknownEmployeeStatus() {
    assertThatThrownBy(() -> service.sync(snapshot("NV1", "a@example.com", "UNKNOWN", true)))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Trạng thái nhân viên không hợp lệ");
  }

  @Test
  void rejectsInboundSyncWhenWebhookKeyIsNotConfigured() {
    assertThatThrownBy(() -> controller.sync(null, snapshot("NV1", "a@example.com", "ACTIVE", true)))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Chưa cấu hình khóa tích hợp");
  }

  @Test
  void usersEndpointDoesNotExposePasswordHash() {
    service.sync(snapshot("NV1", "a@example.com", "ACTIVE", true));
    User persisted = users.findByHrmCode("NV1").orElseThrow();
    persisted.passwordHash = "khong-duoc-tra-ve-api";
    users.saveAndFlush(persisted);

    List<UserView> response = controller.users().getBody();
    assertThat(response).hasSize(1);
    assertThat(response.getFirst().hrmCode()).isEqualTo("NV1");
    assertThat(UserView.class.getRecordComponents())
        .extracting(component -> component.getName())
        .doesNotContain("passwordHash");
  }

  private static SyncRequest snapshot(String code, String email, String status, boolean fullSnapshot) {
    return new SyncRequest(
        List.of(new DepartmentPayload("IT", "Công nghệ thông tin")),
        List.of(new EmployeePayload(code, "Nguyễn Văn A", email, null, "IT", status, "ATTENDEE")),
        fullSnapshot,
        "HRM-ERP",
        null
    );
  }
}
