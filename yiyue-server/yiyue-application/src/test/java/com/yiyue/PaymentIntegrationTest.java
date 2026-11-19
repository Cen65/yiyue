package com.yiyue;

import static org.assertj.core.api.Assertions.*;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yiyue.auth.SessionUser;
import com.yiyue.common.BusinessException;
import com.yiyue.mapper.*;
import com.yiyue.model.entity.*;
import com.yiyue.model.req.*;
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
class PaymentIntegrationTest {
  @Autowired AutowireCapableBeanFactory factory;
  @Autowired PaymentService payments;
  @Autowired PaymentOrderMapper orders;

  @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
  MockPaymentReceiptMapper receipts;

  @Autowired ScheduleMapper schedules;
  @Autowired ScheduleChangeService changes;
  BookingIntegrationTest f;
  long scheduleId;

  @BeforeEach
  void seed() {
    f = new BookingIntegrationTest();
    factory.autowireBean(f);
    f.seed();
    scheduleId = f.slots.selectById(f.slotId).getScheduleId();
    schedules.update(
        Wrappers.<ScheduleDO>lambdaUpdate()
            .eq(ScheduleDO::getId, scheduleId)
            .set(ScheduleDO::getFeeCents, 2000));
  }

  private com.yiyue.model.resp.PaymentResp create() {
    return payments.create(f.patient, new PaymentCreateReq(f.slotId, null, "order-one"));
  }

  @Test
  void reservePayAndDuplicateCallbackAreIdempotent() {
    var o = create();
    assertThat(create().id()).isEqualTo(o.id());
    assertThat(o.amountCents()).isEqualTo(2000);
    assertThat(f.slots.selectById(f.slotId).getReserved()).isEqualTo(1);
    assertThatThrownBy(() -> f.booking.book(f.patient, f.slotId, "bypass"))
        .isInstanceOf(BusinessException.class);
    var paid = payments.mockPay(f.patient, o.id());
    assertThat(paid.status()).isEqualTo("PAID");
    assertThat(payments.mockPay(f.patient, o.id()).appointmentId()).isEqualTo(paid.appointmentId());
    assertThat(f.slots.selectById(f.slotId).getReserved()).isEqualTo(1);
    assertThat(payments.reconcile(f.admin).getFirst().get("matched")).isEqualTo(true);
  }

  @Test
  void timeoutAndLatePaymentRefundWithoutBooking() {
    var o = create();
    orders.update(
        Wrappers.<PaymentOrderDO>lambdaUpdate()
            .eq(PaymentOrderDO::getId, o.id())
            .set(PaymentOrderDO::getExpiresAt, java.time.LocalDateTime.now().minusDays(1)));
    payments.poll();
    assertThat(orders.selectById(o.id()).getStatus()).isEqualTo("CLOSED");
    assertThat(f.slots.selectById(f.slotId).getReserved()).isZero();
    assertThat(payments.mockPay(f.patient, o.id()).status()).isEqualTo("REFUND_PENDING");
    payments.poll();
    payments.poll();
    assertThat(orders.selectById(o.id()).getStatus()).isEqualTo("REFUNDED");
    assertThat(orders.selectById(o.id()).getAppointmentId()).isNull();
    assertThat(payments.reconcile(f.admin).getFirst().get("matched")).isEqualTo(true);
  }

  @Test
  void cancelPaidAppointmentTriggersRefund() {
    var paid = payments.mockPay(f.patient, create().id());
    f.booking.cancel(f.patient, paid.appointmentId());
    assertThat(orders.selectById(paid.id()).getStatus()).isEqualTo("REFUND_PENDING");
    payments.poll();
    assertThat(orders.selectById(paid.id()).getStatus()).isEqualTo("REFUNDED");
    assertThat(f.slots.selectById(f.slotId).getReserved()).isZero();
  }

  @Test
  void paymentAndExpirationRaceNeverDoubleRelease() throws Exception {
    var o = create();
    orders.update(
        Wrappers.<PaymentOrderDO>lambdaUpdate()
            .eq(PaymentOrderDO::getId, o.id())
            .set(PaymentOrderDO::getExpiresAt, java.time.LocalDateTime.now().minusDays(1)));
    try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var start = new java.util.concurrent.CountDownLatch(1);
      var a =
          pool.submit(
              () -> {
                start.await();
                payments.mockPay(f.patient, o.id());
                return null;
              });
      var b =
          pool.submit(
              () -> {
                start.await();
                payments.poll();
                return null;
              });
      start.countDown();
      a.get();
      b.get();
    }
    payments.poll();
    assertThat(f.slots.selectById(f.slotId).getReserved()).isZero();
    assertThat(orders.selectById(o.id()).getStatus()).isEqualTo("REFUNDED");
    assertThat(orders.selectById(o.id()).getAppointmentId()).isNull();
  }

  @Test
  void stoppedScheduleClosesUnpaidAndRefundsPaid() {
    var o = create();
    changes.create(f.admin, scheduleId, new ScheduleChangeReq("停诊", "STOP", null));
    assertThat(orders.selectById(o.id()).getStatus()).isEqualTo("CLOSED");
    assertThat(f.slots.selectById(f.slotId).getReserved()).isZero();
    assertThat(payments.mockPay(f.patient, o.id()).status()).isEqualTo("REFUND_PENDING");
    payments.poll();
    assertThat(orders.selectById(o.id()).getStatus()).isEqualTo("REFUNDED");
  }

  @Test
  void ownershipAndReconciliationMismatch() {
    var o = create();
    SessionUser other = f.user("other", "PATIENT");
    assertThatThrownBy(() -> payments.mockPay(other, o.id())).isInstanceOf(BusinessException.class);
    assertThat(payments.mine(other)).isEmpty();
    payments.mockPay(f.patient, o.id());
    var rec =
        receipts.selectOne(
            Wrappers.<MockPaymentReceiptDO>lambdaQuery()
                .eq(MockPaymentReceiptDO::getOrderId, o.id()));
    rec.setAmountCents(1);
    receipts.updateById(rec);
    assertThat(payments.reconcile(f.admin).getFirst().get("matched")).isEqualTo(false);
  }

  @Test
  void revokedAuthorizationRefundsInsteadOfBooking() {
    var profile =
        factory
            .getBean(PatientProfileService.class)
            .save(f.patient, null, new PatientProfileReq("虚构家属", "OTHER", true));
    var o = payments.create(f.patient, new PaymentCreateReq(f.slotId, profile.getId(), "family"));
    factory.getBean(PatientProfileService.class).revoke(f.patient, profile.getId());
    assertThat(payments.mockPay(f.patient, o.id()).status()).isEqualTo("REFUND_PENDING");
    assertThat(f.slots.selectById(f.slotId).getReserved()).isZero();
  }

  @Test
  void stoppingPaidAppointmentRefundIsInCancellationTask() {
    var o = payments.mockPay(f.patient, create().id());
    var change = changes.create(f.admin, scheduleId, new ScheduleChangeReq("停诊", "STOP", null));
    changes.process(f.admin, change.id());
    assertThat(orders.selectById(o.id()).getStatus()).isEqualTo("REFUND_PENDING");
    assertThat(f.appointments.selectById(o.appointmentId()).getStatus()).isEqualTo("CANCELLED");
    payments.poll();
    assertThat(orders.selectById(o.id()).getStatus()).isEqualTo("REFUNDED");
  }

  @Test
  void failedRefundRollsBackAndRetries() {
    var paid = payments.mockPay(f.patient, create().id());
    f.booking.cancel(f.patient, paid.appointmentId());
    org.mockito.Mockito.doThrow(new IllegalStateException("模拟退款故障"))
        .when(receipts)
        .updateById(
            org.mockito.ArgumentMatchers.<MockPaymentReceiptDO>argThat(
                r -> r != null && r.getOrderId().equals(paid.id())));
    payments.poll();
    assertThat(orders.selectById(paid.id()).getStatus()).isEqualTo("REFUND_PENDING");
    assertThat(
            receipts
                .selectOne(
                    Wrappers.<MockPaymentReceiptDO>lambdaQuery()
                        .eq(MockPaymentReceiptDO::getOrderId, paid.id()))
                .getRefunded())
        .isFalse();
    org.mockito.Mockito.reset(receipts);
    payments.poll();
    assertThat(orders.selectById(paid.id()).getStatus()).isEqualTo("REFUNDED");
  }
}
