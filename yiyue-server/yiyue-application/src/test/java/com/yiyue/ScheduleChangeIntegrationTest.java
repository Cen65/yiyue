package com.yiyue;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yiyue.common.BusinessException;
import com.yiyue.mapper.*;
import com.yiyue.model.entity.*;
import com.yiyue.model.req.*;
import com.yiyue.service.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ScheduleChangeIntegrationTest {
  @Autowired AutowireCapableBeanFactory factory;
  @Autowired ScheduleChangeService changes;
  @Autowired ScheduleMapper schedules;
  @Autowired ScheduleChangeItemMapper items;
  BookingIntegrationTest f;
  long scheduleId;

  @BeforeEach
  void seed() {
    f = new BookingIntegrationTest();
    factory.autowireBean(f);
    f.seed();
    scheduleId = f.slots.selectById(f.slotId).getScheduleId();
  }

  private com.yiyue.model.resp.ScheduleChangeResp stop() {
    return changes.create(f.admin, scheduleId, new ScheduleChangeReq("医生临时停诊", "STOP", null));
  }

  private long replacement() {
    var source = schedules.selectById(scheduleId);
    var d = new DoctorDO();
    d.setTenantId(f.tenantId);
    d.setDepartmentId(source.getDepartmentId());
    d.setName("替诊医生");
    d.setTitle("测试");
    d.setSpecialty("测试");
    f.doctors.insert(d);
    return d.getId();
  }

  @Test
  void stopPersistsImpactAndCancelsExactlyOnce() {
    var a = f.booking.book(f.patient, f.slotId, "source");
    var job = stop();
    assertThat(job.total()).isEqualTo(1);
    assertThat(changes.mine(f.patient)).hasSize(1);
    assertThat(schedules.selectById(scheduleId).getStatus()).isEqualTo("STOPPED");
    assertThatThrownBy(() -> f.booking.book(f.user("other", "PATIENT"), f.slotId, "blocked"))
        .isInstanceOf(BusinessException.class);
    assertThat(f.appointments.selectById(a.getId()).getStatus()).isEqualTo("BOOKED");
    changes.process(f.admin, job.id());
    changes.process(f.admin, job.id());
    assertThat(f.appointments.selectById(a.getId()).getStatus()).isEqualTo("CANCELLED");
    assertThat(f.slots.selectById(f.slotId).getReserved()).isZero();
    assertThat(changes.list(f.admin).getFirst().status()).isEqualTo("DONE");
    assertThat(stop().id()).isEqualTo(job.id());
  }

  @Test
  void priorPatientCancellationDoesNotDoubleRelease() {
    var a = f.booking.book(f.patient, f.slotId, "source");
    var job = stop();
    f.booking.cancel(f.patient, a.getId());
    changes.process(f.admin, job.id());
    assertThat(f.slots.selectById(f.slotId).getReserved()).isZero();
    assertThat(changes.items(f.admin, job.id()).getFirst().status()).isEqualTo("DONE");
  }

  @Test
  void substituteRequiresPatientDecisionAndIsIdempotent() {
    f.booking.book(f.patient, f.slotId, "source");
    var job =
        changes.create(
            f.admin, scheduleId, new ScheduleChangeReq("替诊安排", "SUBSTITUTE", replacement()));
    var item = changes.mine(f.patient).getFirst();
    var target =
        f.slots
            .selectList(
                Wrappers.<SlotDO>lambdaQuery()
                    .eq(SlotDO::getScheduleId, job.replacementScheduleId()))
            .getFirst();
    assertThatThrownBy(() -> changes.accept(f.patient, item.id(), target.getId()))
        .isInstanceOf(BusinessException.class);
    changes.process(f.admin, job.id());
    assertThat(f.slots.selectById(target.getId()).getReserved()).isZero();
    var booked = changes.accept(f.patient, item.id(), target.getId());
    assertThat(changes.accept(f.patient, item.id(), target.getId()).id()).isEqualTo(booked.id());
    assertThat(changes.mine(f.patient).getFirst().decision()).isEqualTo("ACCEPTED");
    assertThat(f.slots.selectById(target.getId()).getReserved()).isEqualTo(1);
  }

  @Test
  void invalidSubstituteRollsBackStop() {
    assertThatThrownBy(
            () ->
                changes.create(
                    f.admin, scheduleId, new ScheduleChangeReq("测试", "SUBSTITUTE", f.doctorId)))
        .isInstanceOf(BusinessException.class);
    assertThat(schedules.selectById(scheduleId).getStatus()).isEqualTo("OPEN");
    assertThat(changes.list(f.admin)).isEmpty();
  }

  @Test
  void ownershipConsentAndDeclineAreEnforced() throws Exception {
    f.booking.book(f.patient, f.slotId, "source");
    var job = stop();
    var item = changes.mine(f.patient).getFirst();
    var other = f.user("other", "PATIENT");
    assertThatThrownBy(() -> changes.decline(other, item.id()))
        .isInstanceOf(BusinessException.class);
    assertThat(changes.mine(other)).isEmpty();
    assertThatThrownBy(() -> changes.list(f.patient)).isInstanceOf(BusinessException.class);
    var session = new MockHttpSession();
    session.setAttribute("SPRING_SECURITY_CONTEXT", context(f.patient));
    f.mvc
        .perform(
            post("/api/patient/changes/" + item.id() + "/accept")
                .session(session)
                .with(csrf())
                .contentType("application/json")
                .content("{\"slotId\":1,\"consent\":false}"))
        .andExpect(status().isBadRequest());
    changes.decline(f.patient, item.id());
    changes.decline(f.patient, item.id());
    changes.process(f.admin, job.id());
    assertThat(changes.mine(f.patient).getFirst().decision()).isEqualTo("DECLINED");
  }

  private org.springframework.security.core.context.SecurityContext context(
      com.yiyue.auth.SessionUser u) {
    var c = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
    c.setAuthentication(
        new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            u,
            null,
            java.util.List.of(
                new org.springframework.security.core.authority.SimpleGrantedAuthority(
                    "ROLE_PATIENT"))));
    return c;
  }

  @Test
  void fullTargetDoesNotLosePendingDecision() {
    f.booking.book(f.patient, f.slotId, "source");
    var job =
        changes.create(
            f.admin, scheduleId, new ScheduleChangeReq("替诊", "SUBSTITUTE", replacement()));
    changes.process(f.admin, job.id());
    var item = changes.mine(f.patient).getFirst();
    var target =
        f.slots
            .selectList(
                Wrappers.<SlotDO>lambdaQuery()
                    .eq(SlotDO::getScheduleId, job.replacementScheduleId()))
            .getFirst();
    f.booking.book(f.user("other", "PATIENT"), target.getId(), "fill");
    assertThatThrownBy(() -> changes.accept(f.patient, item.id(), target.getId()))
        .isInstanceOf(BusinessException.class);
    assertThat(changes.mine(f.patient).getFirst().decision()).isEqualTo("PENDING");
    assertThat(f.slots.selectById(target.getId()).getReserved()).isEqualTo(1);
  }

  @Test
  void failureRollsBackAndCanRetry() {
    var a = f.booking.book(f.patient, f.slotId, "source");
    var job = stop(); // 模拟容量异常；每次失败必须回滚预约取消。
    f.slots.update(
        Wrappers.<SlotDO>lambdaUpdate().eq(SlotDO::getId, f.slotId).set(SlotDO::getReserved, 0));
    for (int n = 0; n < 3; n++) changes.process(f.admin, job.id());
    assertThat(changes.items(f.admin, job.id()).getFirst().status()).isEqualTo("FAILED");
    assertThat(f.appointments.selectById(a.getId()).getStatus()).isEqualTo("BOOKED");
    f.slots.update(
        Wrappers.<SlotDO>lambdaUpdate().eq(SlotDO::getId, f.slotId).set(SlotDO::getReserved, 1));
    changes.retry(f.admin, job.id());
    changes.process(f.admin, job.id());
    assertThat(changes.items(f.admin, job.id()).getFirst().status()).isEqualTo("DONE");
    assertThat(f.slots.selectById(f.slotId).getReserved()).isZero();
    assertThatThrownBy(() -> changes.retry(f.admin, job.id()))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void concurrentStopCannotMissNewBooking() throws Exception {
    var other = f.user("racer", "PATIENT");
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var booking =
          pool.submit(
              () -> {
                start.await();
                try {
                  f.booking.book(other, f.slotId, "race");
                } catch (BusinessException expected) {
                }
                return null;
              });
      var stopping =
          pool.submit(
              () -> {
                start.await();
                return stop();
              });
      start.countDown();
      booking.get(10, TimeUnit.SECONDS);
      var job = stopping.get(10, TimeUnit.SECONDS);
      changes.process(f.admin, job.id());
      assertThat(
              f.appointments.selectCount(
                  Wrappers.<AppointmentDO>lambdaQuery()
                      .eq(AppointmentDO::getTenantId, f.tenantId)
                      .eq(AppointmentDO::getStatus, "BOOKED")))
          .isZero();
      assertThat(f.slots.selectById(f.slotId).getReserved()).isZero();
    }
  }

  @Autowired ScheduleChangeMapper jobMapper;

  @Test
  void recreatedWorkerResumesDurablePendingTask() {
    var a = f.booking.book(f.patient, f.slotId, "persisted");
    var job = stop();
    // 重新创建轮询器，进度仅来自数据库，不依赖原请求或内存队列。
    new com.yiyue.config.ChangeTaskRunner(jobMapper, changes).process();
    assertThat(f.appointments.selectById(a.getId()).getStatus()).isEqualTo("CANCELLED");
    assertThat(changes.items(f.admin, job.id()).getFirst().status()).isEqualTo("DONE");
  }

  @Test
  void checkedInOrStartedScheduleCannotStop() {
    var a = f.booking.book(f.patient, f.slotId, "arrived");
    f.booking.transition(f.admin, a.getId(), "check-in");
    assertThatThrownBy(this::stop).isInstanceOf(BusinessException.class);
    assertThat(schedules.selectById(scheduleId).getStatus()).isEqualTo("OPEN");
    schedules.update(
        Wrappers.<ScheduleDO>lambdaUpdate()
            .eq(ScheduleDO::getId, scheduleId)
            .set(ScheduleDO::getStartAt, java.time.LocalDateTime.now().minusDays(1)));
    assertThatThrownBy(this::stop).isInstanceOf(BusinessException.class);
    assertThat(changes.list(f.admin)).isEmpty();
  }

  @Test
  void otherTenantCannotReadProcessOrDecide() {
    f.booking.book(f.patient, f.slotId, "source");
    var job = stop();
    var item = changes.mine(f.patient).getFirst();
    var foreign = new BookingIntegrationTest();
    factory.autowireBean(foreign);
    foreign.seed();
    assertThatThrownBy(() -> changes.items(foreign.admin, job.id()))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> changes.process(foreign.admin, job.id()))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> changes.decline(foreign.patient, item.id()))
        .isInstanceOf(BusinessException.class);
    assertThat(changes.list(foreign.admin)).isEmpty();
  }

  @Test
  void proposalMustMatchCampusAndStoppedTargetCannotBeAccepted() {
    f.booking.book(f.patient, f.slotId, "source");
    var job = stop();
    var item = changes.mine(f.patient).getFirst();
    var source = schedules.selectById(scheduleId);
    var start = source.getStartAt().plusDays(1);
    var wrongCampus = new CampusDO();
    wrongCampus.setTenantId(f.tenantId);
    wrongCampus.setName("其他院区");
    wrongCampus.setAddress("虚构");
    f.campuses.insert(wrongCampus);
    var wrong =
        f.scheduling.create(
            f.admin,
            new ScheduleCreateReq(
                wrongCampus.getId(), f.doctorId, start, start.plusHours(1), 30, 1));
    assertThatThrownBy(() -> changes.propose(f.admin, item.id(), wrong.getId()))
        .isInstanceOf(BusinessException.class);
    var target =
        f.scheduling.create(
            f.admin,
            new ScheduleCreateReq(
                f.campusId, f.doctorId, start.plusDays(1), start.plusDays(1).plusHours(1), 30, 1));
    changes.propose(f.admin, item.id(), target.getId());
    changes.process(f.admin, job.id());
    var targetSlot =
        f.slots
            .selectList(Wrappers.<SlotDO>lambdaQuery().eq(SlotDO::getScheduleId, target.getId()))
            .getFirst();
    changes.create(f.admin, target.getId(), new ScheduleChangeReq("目标停诊", "STOP", null));
    assertThatThrownBy(() -> changes.accept(f.patient, item.id(), targetSlot.getId()))
        .isInstanceOf(BusinessException.class);
    assertThat(changes.mine(f.patient).getFirst().decision()).isEqualTo("PENDING");
  }
}
