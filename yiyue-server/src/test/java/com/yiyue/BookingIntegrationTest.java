package com.yiyue;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yiyue.auth.SessionUser;
import com.yiyue.booking.BookingService;
import com.yiyue.common.BusinessException;
import com.yiyue.domain.*;
import com.yiyue.mapper.*;
import com.yiyue.scheduling.ScheduleService;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BookingIntegrationTest {
  @Autowired BookingService booking;
  @Autowired ScheduleService scheduling;
  @Autowired TenantMapper tenants;
  @Autowired AccountMapper accounts;
  @Autowired CampusMapper campuses;
  @Autowired DepartmentMapper departments;
  @Autowired DoctorMapper doctors;
  @Autowired SlotMapper slots;
  @Autowired AppointmentMapper appointments;
  @Autowired PasswordEncoder encoder;
  @Autowired MockMvc mvc;
  @Autowired PlatformTransactionManager txManager;
  long tenantId, slotId, campusId, doctorId;
  String code;
  SessionUser patient, admin;

  @BeforeEach
  void seed() {
    code = "t" + UUID.randomUUID().toString().replace("-", "");
    Tenant t = new Tenant();
    t.setCode(code);
    t.setName("集成测试机构");
    tenants.insert(t);
    tenantId = t.getId();
    patient = user("patient", "PATIENT");
    admin = user("admin", "ADMIN");
    Campus c = new Campus();
    c.setTenantId(tenantId);
    c.setName("测试院区");
    c.setAddress("虚构地址");
    campuses.insert(c);
    campusId = c.getId();
    Department d = new Department();
    d.setTenantId(tenantId);
    d.setName("测试科室");
    departments.insert(d);
    Doctor doc = new Doctor();
    doc.setTenantId(tenantId);
    doc.setDepartmentId(d.getId());
    doc.setName("测试医生");
    doc.setTitle("测试职称");
    doc.setSpecialty("演示");
    doctors.insert(doc);
    doctorId = doc.getId();
    var start = LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(10).atTime(9, 0);
    var schedule =
        scheduling.create(
            admin,
            new ScheduleService.Create(campusId, doctorId, start, start.plusHours(1), 30, 1));
    slotId =
        slots
            .selectList(
                Wrappers.<Slot>lambdaQuery()
                    .eq(Slot::getScheduleId, schedule.getId())
                    .orderByAsc(Slot::getId))
            .getFirst()
            .getId();
  }

  SessionUser user(String username, String role) {
    Account a = new Account();
    a.setTenantId(tenantId);
    a.setUsername(username);
    a.setDisplayName("测试账号");
    a.setPasswordHash(encoder.encode("test-only-password"));
    a.setRole(role);
    accounts.insert(a);
    return new SessionUser(a.getId(), tenantId, a.getDisplayName(), role);
  }

  @Test
  void idempotentRetryAndCancellationReleaseOnlyOnce() {
    Appointment a = booking.book(patient, slotId, "request-one");
    assertThat(booking.book(patient, slotId, "request-one").getId()).isEqualTo(a.getId());
    assertThat(slots.selectById(slotId).getReserved()).isEqualTo(1);
    booking.cancel(patient, a.getId());
    booking.cancel(patient, a.getId());
    assertThat(slots.selectById(slotId).getReserved()).isZero();
    assertThat(booking.book(patient, slotId, "request-two").getStatus()).isEqualTo("BOOKED");
  }

  @Test
  void lastSlotAllowsExactlyOneConcurrentBooking() throws Exception {
    List<SessionUser> contenders = new ArrayList<>();
    for (int n = 0; n < 12; n++) contenders.add(user("p" + n, "PATIENT"));
    CountDownLatch start = new CountDownLatch(1);
    AtomicInteger success = new AtomicInteger();
    try (var pool = Executors.newFixedThreadPool(12)) {
      List<Future<?>> futures = new ArrayList<>();
      for (var contender : contenders)
        futures.add(
            pool.submit(
                () -> {
                  try {
                    start.await();
                    booking.book(contender, slotId, UUID.randomUUID().toString());
                    success.incrementAndGet();
                  } catch (BusinessException ex) {
                    assertThat(ex.getMessage()).contains("号源已满");
                  } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(ex);
                  }
                }));
      start.countDown();
      for (var f : futures) f.get(20, TimeUnit.SECONDS);
    }
    assertThat(success.get()).isEqualTo(1);
    assertThat(slots.selectById(slotId).getReserved()).isEqualTo(1);
  }

  @Test
  void transactionRollbackRestoresCapacityAndAppointment() {
    new TransactionTemplate(txManager)
        .executeWithoutResult(
            status -> {
              booking.book(patient, slotId, "rollback");
              status.setRollbackOnly();
            });
    assertThat(slots.selectById(slotId).getReserved()).isZero();
    assertThat(
            appointments.selectCount(
                Wrappers.<Appointment>lambdaQuery().eq(Appointment::getTenantId, tenantId)))
        .isZero();
  }

  @Test
  void tenantAndPatientBoundariesAreEnforced() {
    var outsider = new SessionUser(patient.id(), tenantId + 999999, "其他机构", "PATIENT");
    assertThatThrownBy(() -> booking.book(outsider, slotId, "foreign"))
        .isInstanceOf(BusinessException.class);
    Appointment a = booking.book(patient, slotId, "own");
    assertThatThrownBy(() -> booking.cancel(user("other", "PATIENT"), a.getId()))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> booking.cancel(outsider, a.getId()))
        .isInstanceOf(BusinessException.class);
    assertThat(slots.selectById(slotId).getReserved()).isEqualTo(1);
  }

  @Test
  void invalidStateTransitionIsRejected() {
    Appointment a = booking.book(patient, slotId, "states");
    assertThatThrownBy(() -> booking.transition(admin, a.getId(), "complete"))
        .isInstanceOf(BusinessException.class);
    booking.transition(admin, a.getId(), "check-in");
    assertThatThrownBy(() -> booking.cancel(patient, a.getId()))
        .isInstanceOf(BusinessException.class);
    booking.transition(admin, a.getId(), "complete");
    assertThat(appointments.selectById(a.getId()).getStatus()).isEqualTo("COMPLETED");
  }

  @Test
  void overlappingScheduleIsRejectedAcrossCampuses() {
    var start = LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(10).atTime(9, 15);
    assertThatThrownBy(
            () ->
                scheduling.create(
                    admin,
                    new ScheduleService.Create(
                        campusId, doctorId, start, start.plusHours(1), 30, 1)))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("重叠");
  }

  @Test
  void loginRequiresCsrfAndPatientCannotAccessAdmin() throws Exception {
    String body =
        "{\"tenantCode\":\""
            + code
            + "\",\"username\":\"patient\",\"password\":\"test-only-password\"}";
    mvc.perform(get("/api/catalog")).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/auth/login").contentType("application/json").content(body))
        .andExpect(status().isForbidden());
    var result =
        mvc.perform(
                post("/api/auth/login").with(csrf()).contentType("application/json").content(body))
            .andExpect(status().isOk())
            .andReturn();
    var session = (MockHttpSession) result.getRequest().getSession();
    mvc.perform(get("/api/catalog").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.campuses[0].id").value(campusId));
    mvc.perform(get("/api/admin/appointments").session(session)).andExpect(status().isForbidden());
    mvc.perform(
            post("/api/patient/appointments")
                .session(session)
                .with(csrf())
                .contentType("application/json")
                .content("{\"slotId\":-1,\"requestKey\":\"a\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void triageRequiresPatientConsentAndReportsDisabledConfiguration() throws Exception {
    String body =
        """
        {"tenantCode":"%s","username":"patient","password":"test-only-password"}
        """
            .formatted(code);
    var result =
        mvc.perform(
                post("/api/auth/login").with(csrf()).contentType("application/json").content(body))
            .andExpect(status().isOk())
            .andReturn();
    var session = (MockHttpSession) result.getRequest().getSession();
    mvc.perform(get("/api/patient/triage/status").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled").value(false));
    mvc.perform(
            post("/api/patient/triage")
                .session(session)
                .with(csrf())
                .contentType("application/json")
                .content(
                    """
        {"symptoms":"虚构测试症状描述","consent":false}
        """))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/patient/triage")
                .session(session)
                .with(csrf())
                .contentType("application/json")
                .content(
                    """
        {"symptoms":"虚构测试症状描述","consent":true}
        """))
        .andExpect(status().isServiceUnavailable());
    mvc.perform(
            post("/api/patient/triage")
                .session(session)
                .contentType("application/json")
                .content(
                    """
        {"symptoms":"虚构测试症状描述","consent":true}
        """))
        .andExpect(status().isForbidden());
  }
}
