package com.yiyue;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yiyue.auth.SessionUser;
import com.yiyue.common.BusinessException;
import com.yiyue.mapper.*;
import com.yiyue.model.entity.*;
import com.yiyue.model.query.ScheduleQuery;
import com.yiyue.model.req.*;
import com.yiyue.model.resp.*;
import com.yiyue.service.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OperationsIntegrationTest {
  @Autowired AutowireCapableBeanFactory factory;
  @Autowired OperationsService operations;
  @Autowired PatientProfileService profiles;
  @Autowired BookingService bookings;
  @Autowired CatalogService catalog;
  @Autowired AppointmentQueryService queries;
  @Autowired ScheduleService scheduling;
  @Autowired DoctorMapper doctors;
  @Autowired AccountMapper accounts;
  @Autowired MockMvc mvc;
  @Autowired PlatformService platform;
  BookingIntegrationTest fixture;

  @BeforeEach
  void seed() {
    fixture = new BookingIntegrationTest();
    factory.autowireBean(fixture);
    fixture.seed();
  }

  OperationsReq resource(String name, boolean enabled) {
    return new OperationsReq(name, "虚构测试地址", "测试医师", "测试专长", null, null, null, enabled);
  }

  @Test
  void duplicateNamesAndCrossTenantUpdatesAreRejected() {
    var c = (CampusResp) operations.save(fixture.admin, "campuses", null, resource("新增院区", true));
    assertThatThrownBy(
            () -> operations.save(fixture.admin, "campuses", null, resource("新增院区", true)))
        .isInstanceOf(BusinessException.class);
    var foreign = new SessionUser(fixture.admin.id(), fixture.tenantId + 999999, "外部", "ADMIN");
    assertThatThrownBy(() -> operations.save(foreign, "campuses", c.id(), resource("越权修改", true)))
        .isInstanceOf(BusinessException.class);
    var disabled =
        (CampusResp) operations.save(fixture.admin, "campuses", c.id(), resource("新增院区", false));
    assertThat(disabled.enabled()).isFalse();
    assertThat(catalog.catalog(fixture.patient).campuses()).noneMatch(v -> v.id().equals(c.id()));
  }

  @Test
  void activeSchedulePreventsDisablingResources() {
    var departmentId = doctors.selectById(fixture.doctorId).getDepartmentId();
    assertThatThrownBy(
            () ->
                operations.save(
                    fixture.admin, "departments", departmentId, resource("测试科室", false)))
        .isInstanceOf(BusinessException.class);

    assertThatThrownBy(
            () ->
                operations.save(
                    fixture.admin, "campuses", fixture.campusId, resource("测试院区", false)))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("排班");
  }

  @Test
  void doctorAssociationsAreTenantScopedAndUsedBySchedules() {
    var dep =
        (DepartmentResp)
            operations.save(fixture.admin, "departments", null, resource("第二科室", true));
    var c = (CampusResp) operations.save(fixture.admin, "campuses", null, resource("第二院区", true));
    var existing = doctors.selectById(fixture.doctorId);
    var doctor =
        (DoctorResp)
            operations.save(
                fixture.admin,
                "doctors",
                fixture.doctorId,
                new OperationsReq(
                    "测试医生",
                    null,
                    "医师",
                    "测试",
                    null,
                    List.of(existing.getDepartmentId(), dep.id()),
                    List.of(fixture.campusId, c.id()),
                    true));
    var start = LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(11).atTime(9, 0);
    var schedule =
        scheduling.create(
            fixture.admin,
            new ScheduleCreateReq(c.id(), doctor.id(), start, start.plusHours(1), 30, 2, dep.id()));
    assertThat(schedule.getDepartmentId()).isEqualTo(dep.id());
    assertThatThrownBy(
            () ->
                operations.save(
                    fixture.admin,
                    "doctors",
                    doctor.id(),
                    new OperationsReq(
                        "测试医生", null, "医师", "测试", null, List.of(99999999L), List.of(c.id()), true)))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(
            () ->
                operations.save(
                    fixture.admin,
                    "doctors",
                    doctor.id(),
                    new OperationsReq(
                        "测试医生",
                        null,
                        "医师",
                        "测试",
                        null,
                        List.of(existing.getDepartmentId()),
                        List.of(fixture.campusId),
                        true)))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("排班");
  }

  @Test
  void bookingRequiresOwnedAuthorizedPatientAndAllowsCancellationAfterRevocation() {
    var child = profiles.save(fixture.patient, null, new PatientProfileReq("虚构家属", "CHILD", true));
    var other = fixture.user("other", "PATIENT");
    assertThatThrownBy(() -> bookings.book(other, fixture.slotId, "foreign-profile", child.getId()))
        .isInstanceOf(BusinessException.class);
    var a = bookings.book(fixture.patient, fixture.slotId, "family", child.getId());
    assertThat(a.getPatientId()).isEqualTo(child.getId());
    profiles.revoke(fixture.patient, child.getId());
    bookings.cancel(fixture.patient, a.getId());
    assertThatThrownBy(
            () -> bookings.book(fixture.patient, fixture.slotId, "revoked", child.getId()))
        .isInstanceOf(BusinessException.class);
    assertThat(fixture.slots.selectById(fixture.slotId).getReserved()).isZero();
  }

  @Test
  void doctorAndReceptionHaveDifferentActionsAndDoctorDataScope() {
    operations.createStaff(
        fixture.admin,
        new StaffCreateReq(
            "doctor", "fictional-test-password", "测试医生账号", "DOCTOR", fixture.doctorId));
    operations.createStaff(
        fixture.admin,
        new StaffCreateReq("desk", "fictional-test-password", "测试前台", "RECEPTION", null));
    var d =
        accounts.selectOne(
            Wrappers.<AccountDO>lambdaQuery()
                .eq(AccountDO::getTenantId, fixture.tenantId)
                .eq(AccountDO::getUsername, "doctor"));
    var r =
        accounts.selectOne(
            Wrappers.<AccountDO>lambdaQuery()
                .eq(AccountDO::getTenantId, fixture.tenantId)
                .eq(AccountDO::getUsername, "desk"));
    var doctor = new SessionUser(d.getId(), fixture.tenantId, d.getDisplayName(), d.getRole());
    var desk = new SessionUser(r.getId(), fixture.tenantId, r.getDisplayName(), r.getRole());
    var a = bookings.book(fixture.patient, fixture.slotId, "staff");
    assertThat(queries.list(doctor, true, 1).records()).hasSize(1);
    assertThatThrownBy(() -> bookings.transition(doctor, a.getId(), "check-in"))
        .isInstanceOf(BusinessException.class);
    bookings.transition(desk, a.getId(), "check-in");
    assertThatThrownBy(() -> bookings.transition(desk, a.getId(), "complete"))
        .isInstanceOf(BusinessException.class);
    bookings.transition(doctor, a.getId(), "complete");
    var outsider = fixture.user("unbound", "DOCTOR");
    assertThat(queries.list(outsider, true, 1).records()).isEmpty();
    assertThatThrownBy(() -> bookings.transition(outsider, a.getId(), "complete"))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void schedulePaginationAndFiltersHaveConsistentTotals() {
    var query = new ScheduleQuery(1, 1, fixture.campusId, null, null, null);
    var result = catalog.page(fixture.patient, query);
    assertThat(result.total()).isEqualTo(1);
    assertThat(result.records()).hasSize(1);
    assertThat(
            catalog
                .page(fixture.patient, new ScheduleQuery(1, 20, 9999999L, null, null, null))
                .total())
        .isZero();
    assertThat(
            catalog
                .page(
                    new SessionUser(fixture.patient.id(), 9999999L, "外部", "PATIENT"),
                    new ScheduleQuery(1, 20, null, null, null, null))
                .records())
        .isEmpty();
  }

  @Test
  void disabledAccountCannotKeepUsingExistingSession() throws Exception {
    String body =
        """
      {"tenantCode":"%s","username":"patient","password":"test-only-password"}
      """
            .formatted(fixture.code);
    var login =
        mvc.perform(
                post("/api/auth/login").with(csrf()).contentType("application/json").content(body))
            .andExpect(status().isOk())
            .andReturn();
    var session = (MockHttpSession) login.getRequest().getSession();
    accounts.update(
        Wrappers.<AccountDO>lambdaUpdate()
            .eq(AccountDO::getId, fixture.patient.id())
            .set(AccountDO::getEnabled, false));
    mvc.perform(get("/api/catalog").session(session)).andExpect(status().isUnauthorized());
  }

  @Test
  void platformProvisioningIsSeparateFromTenantAdminAndDisabledTenantLosesSession()
      throws Exception {
    var input =
        new TenantCreateReq("org" + fixture.code, "虚构新机构", "admin", "fictional-test-password");
    assertThatThrownBy(() -> platform.create(fixture.admin, input))
        .isInstanceOf(BusinessException.class);
    var operator = fixture.user("platform_operator", "PLATFORM_ADMIN");
    var tenant = platform.create(operator, input);
    String body =
        """
      {"tenantCode":"%s","username":"admin","password":"fictional-test-password"}
      """
            .formatted(input.code());
    var login =
        mvc.perform(
                post("/api/auth/login").with(csrf()).contentType("application/json").content(body))
            .andExpect(status().isOk())
            .andReturn();
    var session = (MockHttpSession) login.getRequest().getSession();
    mvc.perform(get("/api/platform/tenants").session(session)).andExpect(status().isForbidden());
    platform.update(operator, tenant.id(), new TenantUpdateReq("虚构新机构", false));
    mvc.perform(get("/api/catalog").session(session)).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json").content(body))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void differentAuthorizedPatientsCanShareCapacityButSamePatientCannotDuplicate() {
    fixture.slots.update(
        Wrappers.<SlotDO>lambdaUpdate()
            .eq(SlotDO::getId, fixture.slotId)
            .set(SlotDO::getCapacity, 3));
    var one = profiles.save(fixture.patient, null, new PatientProfileReq("虚构家属一", "CHILD", true));
    var two = profiles.save(fixture.patient, null, new PatientProfileReq("虚构家属二", "PARENT", true));
    bookings.book(fixture.patient, fixture.slotId, "one", one.getId());
    bookings.book(fixture.patient, fixture.slotId, "two", two.getId());
    assertThatThrownBy(
            () -> bookings.book(fixture.patient, fixture.slotId, "duplicate", one.getId()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThat(fixture.slots.selectById(fixture.slotId).getReserved()).isEqualTo(2);
  }

  @Test
  void overlongMultibytePasswordIsRejectedBeforeAccountCreation() {
    assertThatThrownBy(
            () ->
                operations.createStaff(
                    fixture.admin,
                    new StaffCreateReq("long_password", "测试".repeat(20), "测试", "RECEPTION", null)))
        .isInstanceOf(BusinessException.class);
  }
}
