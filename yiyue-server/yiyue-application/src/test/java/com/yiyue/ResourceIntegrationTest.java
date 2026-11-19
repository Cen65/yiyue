package com.yiyue;

import static org.assertj.core.api.Assertions.*;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yiyue.common.BusinessException;
import com.yiyue.mapper.*;
import com.yiyue.model.entity.*;
import com.yiyue.model.req.*;
import com.yiyue.service.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ResourceIntegrationTest {
  @Autowired AutowireCapableBeanFactory factory;
  @Autowired ResourceService resources;
  @Autowired ScheduleChangeService changes;
  @Autowired ScheduleMapper schedules;
  @Autowired ScheduleResourceMapper reservations;
  BookingIntegrationTest f;
  long doctor;
  java.time.LocalDateTime start;

  @BeforeEach
  void seed() {
    f = new BookingIntegrationTest();
    factory.autowireBean(f);
    f.seed();
    start =
        schedules.selectById(f.slots.selectById(f.slotId).getScheduleId()).getStartAt().plusDays(1);
    var d = new DoctorDO();
    d.setTenantId(f.tenantId);
    d.setName("资源测试医生");
    d.setDepartmentId(f.doctors.selectById(f.doctorId).getDepartmentId());
    d.setTitle("测试");
    d.setSpecialty("演示");
    f.doctors.insert(d);
    doctor = d.getId();
  }

  private long resource(String name, String kind) {
    return resources.save(f.admin, null, new ResourceSaveReq(f.campusId, kind, name, true)).id();
  }

  private ScheduleDO create(long doc, java.time.LocalDateTime time, List<Long> ids) {
    return f.scheduling.create(
        f.admin,
        new ScheduleCreateReq(f.campusId, doc, time, time.plusHours(1), 30, 1, null, 0, ids));
  }

  @Test
  void resourceConflictRollsBackScheduleAndOtherReservations() {
    long room = resource("诊室", "ROOM"), device = resource("设备", "EQUIPMENT");
    create(doctor, start, List.of(device));
    long before =
        schedules.selectCount(
            Wrappers.<ScheduleDO>lambdaQuery().eq(ScheduleDO::getTenantId, f.tenantId));
    assertThatThrownBy(() -> create(f.doctorId, start, List.of(room, device)))
        .isInstanceOf(BusinessException.class);
    assertThat(
            schedules.selectCount(
                Wrappers.<ScheduleDO>lambdaQuery().eq(ScheduleDO::getTenantId, f.tenantId)))
        .isEqualTo(before);
    assertThat(
            reservations.selectCount(
                Wrappers.<ScheduleResourceDO>lambdaQuery()
                    .eq(ScheduleResourceDO::getResourceId, room)))
        .isZero();
  }

  @Test
  void stopReleasesAndAdjacentIntervalsAllowed() {
    long room = resource("诊室", "ROOM");
    var first = create(doctor, start, List.of(room));
    create(doctor, start.plusHours(1), List.of(room));
    changes.create(f.admin, first.getId(), new ScheduleChangeReq("停诊", "STOP", null));
    create(f.doctorId, start, List.of(room));
    assertThat(
            reservations.selectCount(
                Wrappers.<ScheduleResourceDO>lambdaQuery()
                    .eq(ScheduleResourceDO::getScheduleId, first.getId())
                    .eq(ScheduleResourceDO::getStatus, "ACTIVE")))
        .isZero();
  }

  @Test
  void substituteTransfersResourcesAtomically() {
    long room = resource("诊室", "ROOM");
    var first = create(doctor, start, List.of(room));
    var changed =
        changes.create(
            f.admin, first.getId(), new ScheduleChangeReq("替诊", "SUBSTITUTE", f.doctorId));
    assertThat(resources.ids(f.tenantId, changed.replacementScheduleId())).containsExactly(room);
    assertThat(
            reservations.selectCount(
                Wrappers.<ScheduleResourceDO>lambdaQuery()
                    .eq(ScheduleResourceDO::getResourceId, room)
                    .eq(ScheduleResourceDO::getStatus, "ACTIVE")))
        .isEqualTo(1);
  }

  @Test
  void invalidSubstitutePreservesOriginalResource() {
    long room = resource("诊室", "ROOM");
    var first = create(doctor, start, List.of(room));
    create(f.doctorId, start, List.of());
    assertThatThrownBy(
            () ->
                changes.create(
                    f.admin, first.getId(), new ScheduleChangeReq("替诊", "SUBSTITUTE", f.doctorId)))
        .isInstanceOf(BusinessException.class);
    assertThat(
            reservations.selectCount(
                Wrappers.<ScheduleResourceDO>lambdaQuery()
                    .eq(ScheduleResourceDO::getScheduleId, first.getId())
                    .eq(ScheduleResourceDO::getStatus, "ACTIVE")))
        .isEqualTo(1);
    assertThat(schedules.selectById(first.getId()).getStatus()).isEqualTo("OPEN");
  }

  @Test
  void permissionsCampusDisabledAndDuplicateIds() {
    long room = resource("诊室", "ROOM");
    assertThatThrownBy(
            () ->
                resources.save(
                    f.patient, null, new ResourceSaveReq(f.campusId, "ROOM", "越权", true)))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> create(doctor, start, List.of(room, room)))
        .isInstanceOf(BusinessException.class);
    resources.save(f.admin, room, new ResourceSaveReq(f.campusId, "ROOM", "诊室", false));
    assertThatThrownBy(() -> create(doctor, start, List.of(room)))
        .isInstanceOf(BusinessException.class);
    var foreign = new BookingIntegrationTest();
    factory.autowireBean(foreign);
    foreign.seed();
    assertThat(resources.list(foreign.admin)).isEmpty();
    assertThatThrownBy(
            () ->
                resources.save(
                    foreign.admin, room, new ResourceSaveReq(foreign.campusId, "ROOM", "越权", true)))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void occupiedResourceCannotDisable() {
    long room = resource("诊室", "ROOM");
    create(doctor, start, List.of(room));
    assertThatThrownBy(
            () ->
                resources.save(f.admin, room, new ResourceSaveReq(f.campusId, "ROOM", "诊室", false)))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void concurrentDoctorsCannotShareExclusiveResource() throws Exception {
    long room = resource("诊室", "ROOM");
    var count = new java.util.concurrent.atomic.AtomicInteger();
    var latch = new java.util.concurrent.CountDownLatch(1);
    try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var tasks = new ArrayList<java.util.concurrent.Future<?>>();
      for (long d : List.of(doctor, f.doctorId))
        tasks.add(
            pool.submit(
                () -> {
                  try {
                    latch.await();
                    create(d, start, List.of(room));
                    count.incrementAndGet();
                  } catch (BusinessException expected) {
                  } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(ex);
                  }
                }));
      latch.countDown();
      for (var task : tasks) task.get();
    }
    assertThat(count.get()).isEqualTo(1);
  }
}
