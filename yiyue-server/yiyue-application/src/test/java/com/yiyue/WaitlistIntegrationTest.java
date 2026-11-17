package com.yiyue;

import static org.assertj.core.api.Assertions.*;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yiyue.common.BusinessException;
import com.yiyue.mapper.*;
import com.yiyue.model.entity.*;
import com.yiyue.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WaitlistIntegrationTest {
  @Autowired AutowireCapableBeanFactory factory;
  @Autowired WaitlistService service;
  @Autowired WaitlistEntryMapper entries;
  @Autowired NotificationEventMapper events;
  @org.springframework.test.context.bean.override.mockito.MockitoSpyBean InboxMessageMapper inbox;
  @Autowired ScheduleChangeService changes;
  BookingIntegrationTest f;
  com.yiyue.auth.SessionUser p1, p2;
  AppointmentDO booked;

  @BeforeEach
  void seed() {
    f = new BookingIntegrationTest();
    factory.autowireBean(f);
    f.seed();
    p1 = f.user("wait1", "PATIENT");
    p2 = f.user("wait2", "PATIENT");
    booked = f.booking.book(f.patient, f.slotId, "initial");
  }

  @Test
  void fifoHoldCannotBeStolenAndAcceptDoesNotDoubleReserve() {
    var first = service.join(p1, f.slotId, null);
    var second = service.join(p2, f.slotId, null);
    assertThat(service.join(p1, f.slotId, null).getId()).isEqualTo(first.getId());
    f.booking.cancel(f.patient, booked.getId());
    assertThat(entries.selectById(first.getId()).getStatus()).isEqualTo("OFFERED");
    assertThat(entries.selectById(second.getId()).getStatus()).isEqualTo("WAITING");
    assertThatThrownBy(() -> f.booking.book(p2, f.slotId, "steal"))
        .isInstanceOf(BusinessException.class);
    var a = service.accept(p1, first.getId());
    assertThat(service.accept(p1, first.getId()).getId()).isEqualTo(a.getId());
    assertThat(f.slots.selectById(f.slotId).getReserved()).isEqualTo(1);
  }

  @Test
  void expiryAndCancellationPromoteNext() {
    var first = service.join(p1, f.slotId, null);
    var second = service.join(p2, f.slotId, null);
    f.booking.cancel(f.patient, booked.getId());
    entries.update(
        Wrappers.<WaitlistEntryDO>lambdaUpdate()
            .eq(WaitlistEntryDO::getId, first.getId())
            .set(WaitlistEntryDO::getExpiresAt, java.time.LocalDateTime.now().minusDays(1)));
    service.reconcile(f.tenantId, f.slotId);
    assertThat(entries.selectById(first.getId()).getStatus()).isEqualTo("EXPIRED");
    assertThat(entries.selectById(second.getId()).getStatus()).isEqualTo("OFFERED");
    service.cancel(p2, second.getId());
    service.cancel(p2, second.getId());
    assertThat(f.slots.selectById(f.slotId).getReserved()).isZero();
  }

  @Test
  void outboxRecoveryAndDuplicateConsumption() {
    var e = service.join(p1, f.slotId, null);
    f.booking.cancel(f.patient, booked.getId());
    service.poll();
    var messages = service.inbox(p1);
    assertThat(messages).hasSize(1);
    var n =
        events.selectOne(
            Wrappers.<NotificationEventDO>lambdaQuery()
                .eq(NotificationEventDO::getEventKey, "wait-" + e.getId() + "-offered"));
    n.setDelivered(false);
    events.updateById(n);
    service.poll();
    assertThat(service.inbox(p1)).hasSize(1);
    service.read(p1, messages.getFirst().getId());
    assertThat(service.inbox(p1).getFirst().getReadAt()).isNotNull();
  }

  @Test
  void ownershipAndStoppedScheduleReleaseHold() {
    var e = service.join(p1, f.slotId, null);
    assertThatThrownBy(() -> service.cancel(p2, e.getId())).isInstanceOf(BusinessException.class);
    f.booking.cancel(f.patient, booked.getId());
    changes.create(
        f.admin,
        f.slots.selectById(f.slotId).getScheduleId(),
        new com.yiyue.model.req.ScheduleChangeReq("停诊", "STOP", null));
    service.reconcile(f.tenantId, f.slotId);
    assertThat(entries.selectById(e.getId()).getStatus()).isEqualTo("CLOSED");
    assertThat(f.slots.selectById(f.slotId).getReserved()).isZero();
    assertThatThrownBy(() -> service.accept(p1, e.getId())).isInstanceOf(BusinessException.class);
  }

  @Test
  void disabledAccountDoesNotPoisonCancellationTransaction() {
    var e = service.join(p1, f.slotId, null);
    f.accounts.update(
        Wrappers.<AccountDO>lambdaUpdate()
            .eq(AccountDO::getId, p1.id())
            .set(AccountDO::getEnabled, false));
    f.booking.cancel(f.patient, booked.getId());
    assertThat(entries.selectById(e.getId()).getStatus()).isEqualTo("CLOSED");
    assertThat(f.slots.selectById(f.slotId).getReserved()).isZero();
  }

  @Test
  void concurrentConfirmAndCancelNeverDoubleRelease() throws Exception {
    var e = service.join(p1, f.slotId, null);
    f.booking.cancel(f.patient, booked.getId());
    var latch = new java.util.concurrent.CountDownLatch(1);
    try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var a =
          pool.submit(
              () -> {
                latch.await();
                try {
                  service.accept(p1, e.getId());
                } catch (BusinessException expected) {
                }
                return null;
              });
      var b =
          pool.submit(
              () -> {
                latch.await();
                service.cancel(p1, e.getId());
                return null;
              });
      latch.countDown();
      a.get();
      b.get();
    }
    var current = entries.selectById(e.getId());
    assertThat(f.slots.selectById(f.slotId).getReserved())
        .isEqualTo("ACCEPTED".equals(current.getStatus()) ? 1 : 0);
  }

  @Test
  void deliveryFailureLeavesOutboxPendingAndNextPollRecovers() {
    var e = service.join(p1, f.slotId, null);
    f.booking.cancel(f.patient, booked.getId());
    org.mockito.Mockito.doThrow(new IllegalStateException("测试投递失败"))
        .when(inbox)
        .insert(
            org.mockito.ArgumentMatchers.<InboxMessageDO>argThat(
                m -> m != null && m.getUserId().equals(p1.id())));
    service.poll();
    assertThat(service.inbox(p1)).isEmpty();
    assertThat(
            events
                .selectOne(
                    Wrappers.<NotificationEventDO>lambdaQuery()
                        .eq(NotificationEventDO::getEventKey, "wait-" + e.getId() + "-offered"))
                .getDelivered())
        .isFalse();
    org.mockito.Mockito.reset(inbox);
    service.poll();
    assertThat(service.inbox(p1)).hasSize(1);
  }
}
