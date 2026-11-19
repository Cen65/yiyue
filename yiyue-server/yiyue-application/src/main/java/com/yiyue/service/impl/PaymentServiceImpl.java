package com.yiyue.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yiyue.auth.SessionUser;
import com.yiyue.common.BusinessException;
import com.yiyue.mapper.*;
import com.yiyue.model.entity.*;
import com.yiyue.model.req.*;
import com.yiyue.model.resp.*;
import com.yiyue.service.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class PaymentServiceImpl implements PaymentService {
  private final PaymentOrderMapper orders;
  private final DoctorMapper doctors;
  private final CampusMapper campuses;
  private final MockPaymentReceiptMapper receipts;
  private final TenantMapper tenants;
  private final SlotMapper slots;
  private final ScheduleMapper schedules;
  private final AppointmentMapper appointments;
  private final PatientProfileService profiles;
  private final PatientProfileMapper patients;
  private final AccountMapper accounts;
  private final AuditEventMapper audits;
  private final Clock clock;
  private final PlatformTransactionManager transactions;

  private LocalDateTime now() {
    return LocalDateTime.now(clock);
  }

  private PaymentOrderDO own(SessionUser u, long id) {
    var o =
        orders.selectOne(
            Wrappers.<PaymentOrderDO>lambdaQuery()
                .eq(PaymentOrderDO::getTenantId, u.tenantId())
                .eq(PaymentOrderDO::getUserId, u.id())
                .eq(PaymentOrderDO::getId, id));
    if (o == null) throw BusinessException.missing();
    return o;
  }

  private boolean open(SlotDO slot) {
    var s = schedules.selectById(slot.getScheduleId());
    return "OPEN".equals(s.getStatus()) && slot.getStartAt().isAfter(now());
  }

  @Transactional
  public PaymentResp create(SessionUser u, PaymentCreateReq r) {
    tenants.lock(u.tenantId());
    var patient = profiles.requireOwned(u, r.patientId());
    var prior =
        orders.selectOne(
            Wrappers.<PaymentOrderDO>lambdaQuery()
                .eq(PaymentOrderDO::getTenantId, u.tenantId())
                .eq(PaymentOrderDO::getUserId, u.id())
                .eq(PaymentOrderDO::getRequestKey, r.requestKey()));
    if (prior != null) {
      if (!prior.getSlotId().equals(r.slotId()) || !prior.getPatientId().equals(patient.getId()))
        throw BusinessException.conflict("请求标识已用于其他号源或就诊人");
      return view(prior);
    }
    var slot =
        slots.selectOne(
            Wrappers.<SlotDO>lambdaQuery()
                .eq(SlotDO::getTenantId, u.tenantId())
                .eq(SlotDO::getId, r.slotId()));
    if (slot == null) throw BusinessException.missing();
    var schedule = schedules.selectById(slot.getScheduleId());
    if (!open(slot) || schedule.getFeeCents() <= 0)
      throw BusinessException.conflict("仅开放收费排班可创建支付订单");
    if (appointments.exists(
        Wrappers.<AppointmentDO>lambdaQuery()
            .eq(AppointmentDO::getTenantId, u.tenantId())
            .eq(AppointmentDO::getPatientId, patient.getId())
            .eq(AppointmentDO::getSlotId, slot.getId())
            .ne(AppointmentDO::getStatus, "CANCELLED"))) throw BusinessException.conflict("已有有效预约");
    // 金额仅取服务端排班快照；占号与待支付订单一同提交。
    if (slots.reserve(u.tenantId(), slot.getId()) != 1) throw BusinessException.conflict("号源已满");
    var o = new PaymentOrderDO();
    o.setTenantId(u.tenantId());
    o.setUserId(u.id());
    o.setPatientId(patient.getId());
    o.setSlotId(slot.getId());
    o.setRequestKey(r.requestKey());
    o.setAmountCents(schedule.getFeeCents());
    o.setStatus("PENDING");
    o.setCreatedAt(now());
    o.setExpiresAt(
        now().plusMinutes(15).isBefore(slot.getStartAt())
            ? now().plusMinutes(15)
            : slot.getStartAt());
    orders.insert(o);
    audit(u, "PAYMENT_CREATE", o.getId());
    return view(o);
  }

  public List<PaymentResp> mine(SessionUser u) {
    return orders
        .selectPage(
            new Page<>(1, 100),
            Wrappers.<PaymentOrderDO>lambdaQuery()
                .eq(PaymentOrderDO::getTenantId, u.tenantId())
                .eq(PaymentOrderDO::getUserId, u.id())
                .orderByDesc(PaymentOrderDO::getId))
        .getRecords()
        .stream()
        .map(this::view)
        .toList();
  }

  @Transactional
  public PaymentResp mockPay(SessionUser u, long id) {
    tenants.lock(u.tenantId());
    var o = own(u, id);
    var receipt =
        receipts.selectOne(
            Wrappers.<MockPaymentReceiptDO>lambdaQuery().eq(MockPaymentReceiptDO::getOrderId, id));
    if (receipt != null) return view(o);
    // 本接口是已登录患者主动触发的模拟渠道，不是公网支付回调或真实扣款。
    receipt = new MockPaymentReceiptDO();
    receipt.setTenantId(u.tenantId());
    receipt.setOrderId(id);
    receipt.setTransactionKey("mock-" + id);
    receipt.setAmountCents(o.getAmountCents());
    receipt.setRefunded(false);
    receipt.setCreatedAt(now());
    receipts.insert(receipt);
    var slot = slots.selectById(o.getSlotId());
    patients.lock(u.tenantId(), u.id(), o.getPatientId());
    boolean eligible =
        accounts.exists(
                Wrappers.<AccountDO>lambdaQuery()
                    .eq(AccountDO::getId, u.id())
                    .eq(AccountDO::getEnabled, true))
            && patients.exists(
                Wrappers.<PatientProfileDO>lambdaQuery()
                    .eq(PatientProfileDO::getId, o.getPatientId())
                    .eq(PatientProfileDO::getTenantId, u.tenantId())
                    .eq(PatientProfileDO::getOwnerId, u.id())
                    .eq(PatientProfileDO::getAuthorized, true));
    if (!"PENDING".equals(o.getStatus())
        || !o.getExpiresAt().isAfter(now())
        || !open(slot)
        || !eligible) {
      if ("PENDING".equals(o.getStatus())) release(o);
      o.setStatus("REFUND_PENDING");
      orders.updateById(o);
      audit(u, "PAYMENT_LATE_REFUND", id);
      return view(o);
    }
    var a = new AppointmentDO();
    a.setTenantId(u.tenantId());
    a.setUserId(u.id());
    a.setPatientId(o.getPatientId());
    a.setSlotId(o.getSlotId());
    a.setRequestKey("pay-" + id);
    a.setStatus("BOOKED");
    a.setCreatedAt(now());
    appointments.insert(a);
    o.setAppointmentId(a.getId());
    o.setStatus("PAID");
    orders.updateById(o);
    audit(u, "PAYMENT_CONFIRMED", id);
    return view(o);
  }

  private void release(PaymentOrderDO o) {
    if (slots.release(o.getTenantId(), o.getSlotId()) != 1)
      throw BusinessException.conflict("支付保留容量异常");
  }

  @Transactional
  public void cancel(SessionUser u, long id) {
    tenants.lock(u.tenantId());
    var o = own(u, id);
    if (!"PENDING".equals(o.getStatus())) return;
    release(o);
    o.setStatus("CLOSED");
    orders.updateById(o);
    audit(u, "PAYMENT_CLOSE", id);
  }

  @Transactional
  public void onCancelled(long tenant, long appointmentId) {
    tenants.lock(tenant);
    var o =
        orders.selectOne(
            Wrappers.<PaymentOrderDO>lambdaQuery()
                .eq(PaymentOrderDO::getTenantId, tenant)
                .eq(PaymentOrderDO::getAppointmentId, appointmentId)
                .eq(PaymentOrderDO::getStatus, "PAID"));
    if (o == null) return;
    o.setStatus("REFUND_PENDING");
    orders.updateById(o);
    audit(new SessionUser(o.getUserId(), tenant, "系统退款", "PATIENT"), "REFUND_REQUEST", o.getId());
  }

  @Transactional
  public void closeSchedule(long tenant, long scheduleId) {
    tenants.lock(tenant);
    var ids =
        slots
            .selectList(
                Wrappers.<SlotDO>lambdaQuery()
                    .eq(SlotDO::getTenantId, tenant)
                    .eq(SlotDO::getScheduleId, scheduleId))
            .stream()
            .map(SlotDO::getId)
            .toList();
    if (ids.isEmpty()) return;
    for (var o :
        orders.selectList(
            Wrappers.<PaymentOrderDO>lambdaQuery()
                .eq(PaymentOrderDO::getTenantId, tenant)
                .in(PaymentOrderDO::getSlotId, ids)
                .eq(PaymentOrderDO::getStatus, "PENDING"))) {
      release(o);
      o.setStatus("CLOSED");
      orders.updateById(o);
    }
  }

  public void poll() {
    var due =
        orders
            .selectPage(
                new Page<>(1, 100),
                Wrappers.<PaymentOrderDO>lambdaQuery()
                    .and(
                        q ->
                            q.eq(PaymentOrderDO::getStatus, "REFUND_PENDING")
                                .or(
                                    v ->
                                        v.eq(PaymentOrderDO::getStatus, "PENDING")
                                            .le(PaymentOrderDO::getExpiresAt, now())))
                    .orderByAsc(PaymentOrderDO::getId))
            .getRecords();
    var tx = new TransactionTemplate(transactions);
    for (var row : due) {
      try {
        tx.executeWithoutResult(
            s -> {
              tenants.lock(row.getTenantId());
              var o = orders.selectById(row.getId());
              if ("PENDING".equals(o.getStatus()) && !o.getExpiresAt().isAfter(now())) {
                release(o);
                o.setStatus("CLOSED");
                orders.updateById(o);
              } else if ("REFUND_PENDING".equals(o.getStatus())) {
                var receipt =
                    receipts.selectOne(
                        Wrappers.<MockPaymentReceiptDO>lambdaQuery()
                            .eq(MockPaymentReceiptDO::getOrderId, o.getId()));
                if (receipt == null) throw BusinessException.conflict("退款缺少渠道流水");
                receipt.setRefunded(true);
                receipts.updateById(receipt);
                o.setStatus("REFUNDED");
                orders.updateById(o);
                audit(
                    new SessionUser(o.getUserId(), o.getTenantId(), "退款任务", "PATIENT"),
                    "REFUND_DONE",
                    o.getId());
              }
            });
      } catch (RuntimeException ex) {
        log.warn("支付任务{}失败：{}", row.getId(), ex.getClass().getSimpleName());
      }
    }
  }

  public List<Map<String, Object>> reconcile(SessionUser u) {
    if (!"ADMIN".equals(u.role())) throw BusinessException.conflict("仅机构管理员可对账");
    var result = new ArrayList<Map<String, Object>>();
    var list =
        orders
            .selectPage(
                new Page<>(1, 200),
                Wrappers.<PaymentOrderDO>lambdaQuery()
                    .eq(PaymentOrderDO::getTenantId, u.tenantId())
                    .orderByDesc(PaymentOrderDO::getId))
            .getRecords();
    for (var o : list) {
      var rec =
          receipts.selectOne(
              Wrappers.<MockPaymentReceiptDO>lambdaQuery()
                  .eq(MockPaymentReceiptDO::getTenantId, u.tenantId())
                  .eq(MockPaymentReceiptDO::getOrderId, o.getId()));
      boolean needs = List.of("PAID", "REFUND_PENDING", "REFUNDED").contains(o.getStatus());
      boolean ok =
          needs
              ? (rec != null
                  && rec.getAmountCents().equals(o.getAmountCents())
                  && Boolean.TRUE.equals(rec.getRefunded()) == "REFUNDED".equals(o.getStatus()))
              : rec == null;
      result.add(
          Map.of(
              "id",
              o.getId(),
              "status",
              o.getStatus(),
              "amountCents",
              o.getAmountCents(),
              "channel",
              "MOCK",
              "matched",
              ok));
    }
    return result;
  }

  private PaymentResp view(PaymentOrderDO o) {
    var slot = slots.selectById(o.getSlotId());
    var schedule = schedules.selectById(slot.getScheduleId());
    return new PaymentResp(
        o.getId(),
        o.getSlotId(),
        o.getPatientId(),
        o.getAmountCents(),
        o.getStatus(),
        o.getExpiresAt(),
        o.getAppointmentId(),
        "MOCK",
        doctors.selectById(schedule.getDoctorId()).getName(),
        campuses.selectById(schedule.getCampusId()).getName(),
        patients.selectById(o.getPatientId()).getName(),
        slot.getStartAt(),
        slot.getEndAt());
  }

  private void audit(SessionUser u, String action, long id) {
    var e = new AuditEventDO();
    e.setTenantId(u.tenantId());
    e.setActorId(u.id());
    e.setAction(action);
    e.setTargetId(id);
    e.setCreatedAt(now());
    audits.insert(e);
  }
}
