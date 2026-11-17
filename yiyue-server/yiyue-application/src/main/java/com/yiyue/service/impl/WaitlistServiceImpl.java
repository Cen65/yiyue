package com.yiyue.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yiyue.auth.SessionUser;
import com.yiyue.common.BusinessException;
import com.yiyue.mapper.*;
import com.yiyue.model.entity.*;
import com.yiyue.service.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@lombok.extern.slf4j.Slf4j
@RequiredArgsConstructor
public class WaitlistServiceImpl implements WaitlistService {
  private final WaitlistEntryMapper entries;
  private final PatientProfileMapper patients;
  private final AccountMapper accounts;
  private final DoctorMapper doctors;
  private final CampusMapper campuses;
  private final NotificationEventMapper events;
  private final InboxMessageMapper messages;
  private final TenantMapper tenants;
  private final SlotMapper slots;
  private final ScheduleMapper schedules;
  private final AppointmentMapper appointments;
  private final PatientProfileService profiles;
  private final AuditEventMapper audits;
  private final Clock clock;
  private final PlatformTransactionManager transactions;
  private long cursor;

  private LocalDateTime now() {
    return LocalDateTime.now(clock);
  }

  private com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WaitlistEntryDO> active(
      long tenant, long slot) {
    return Wrappers.<WaitlistEntryDO>lambdaQuery()
        .eq(WaitlistEntryDO::getTenantId, tenant)
        .eq(WaitlistEntryDO::getSlotId, slot)
        .in(WaitlistEntryDO::getStatus, List.of("WAITING", "OFFERED"));
  }

  private boolean open(SlotDO slot) {
    var s = schedules.selectById(slot.getScheduleId());
    return s != null && "OPEN".equals(s.getStatus()) && slot.getStartAt().isAfter(now());
  }

  @Transactional
  public WaitlistEntryDO join(SessionUser u, long slotId, Long patientId) {
    tenants.lock(u.tenantId());
    var patient = profiles.requireOwned(u, patientId);
    var slot =
        slots.selectOne(
            Wrappers.<SlotDO>lambdaQuery()
                .eq(SlotDO::getTenantId, u.tenantId())
                .eq(SlotDO::getId, slotId));
    if (slot == null) throw BusinessException.missing();
    if (!open(slot)) throw BusinessException.conflict("排班已关闭或时段已开始");
    var existing =
        entries.selectOne(
            active(u.tenantId(), slotId).eq(WaitlistEntryDO::getPatientId, patient.getId()));
    if (existing != null) {
      if (!existing.getUserId().equals(u.id())) throw BusinessException.conflict("该就诊人已在候补中");
      return existing;
    }
    if (appointments.exists(
        Wrappers.<AppointmentDO>lambdaQuery()
            .eq(AppointmentDO::getTenantId, u.tenantId())
            .eq(AppointmentDO::getSlotId, slotId)
            .eq(AppointmentDO::getPatientId, patient.getId())
            .ne(AppointmentDO::getStatus, "CANCELLED")))
      throw BusinessException.conflict("该就诊人已有预约");
    if (slot.getReserved() < slot.getCapacity()) throw BusinessException.conflict("当前仍有号源，请直接预约");
    var e = new WaitlistEntryDO();
    e.setTenantId(u.tenantId());
    e.setUserId(u.id());
    e.setPatientId(patient.getId());
    e.setSlotId(slotId);
    e.setStatus("WAITING");
    e.setCreatedAt(now());
    entries.insert(e);
    audit(u, "WAIT_JOIN", e.getId());
    return e;
  }

  public List<WaitlistEntryDO> mine(SessionUser u) {
    return entries
        .selectPage(
            new Page<>(1, 100),
            Wrappers.<WaitlistEntryDO>lambdaQuery()
                .eq(WaitlistEntryDO::getTenantId, u.tenantId())
                .eq(WaitlistEntryDO::getUserId, u.id())
                .orderByDesc(WaitlistEntryDO::getId))
        .getRecords();
  }

  private WaitlistEntryDO own(SessionUser u, long id) {
    var e =
        entries.selectOne(
            Wrappers.<WaitlistEntryDO>lambdaQuery()
                .eq(WaitlistEntryDO::getTenantId, u.tenantId())
                .eq(WaitlistEntryDO::getUserId, u.id())
                .eq(WaitlistEntryDO::getId, id));
    if (e == null) throw BusinessException.missing();
    return e;
  }

  @Transactional
  public AppointmentDO accept(SessionUser u, long id) {
    tenants.lock(u.tenantId());
    var e = own(u, id);
    if ("ACCEPTED".equals(e.getStatus())) return appointments.selectById(e.getAppointmentId());
    profiles.requireOwned(u, e.getPatientId());
    var slot = slots.selectById(e.getSlotId());
    if (!"OFFERED".equals(e.getStatus())
        || e.getExpiresAt() == null
        || !e.getExpiresAt().isAfter(now())
        || !open(slot)) throw BusinessException.conflict("候补保留已失效，请刷新");
    // 保留时已经占用容量；确认只把保留转为预约，不能再自增一次。
    var a = new AppointmentDO();
    a.setTenantId(u.tenantId());
    a.setUserId(u.id());
    a.setPatientId(e.getPatientId());
    a.setSlotId(e.getSlotId());
    a.setRequestKey("wait-" + id);
    a.setStatus("BOOKED");
    a.setCreatedAt(now());
    appointments.insert(a);
    e.setStatus("ACCEPTED");
    e.setAppointmentId(a.getId());
    entries.updateById(e);
    notify(e, "accepted", "候补确认成功，新预约 #" + a.getId());
    audit(u, "WAIT_ACCEPT", id);
    return a;
  }

  @Transactional
  public void cancel(SessionUser u, long id) {
    tenants.lock(u.tenantId());
    var e = own(u, id);
    if (!List.of("WAITING", "OFFERED").contains(e.getStatus())) return;
    if ("OFFERED".equals(e.getStatus())) release(e);
    e.setStatus("CANCELLED");
    entries.updateById(e);
    audit(u, "WAIT_CANCEL", id);
    reconcile(u.tenantId(), e.getSlotId());
  }

  private void release(WaitlistEntryDO e) {
    if (slots.release(e.getTenantId(), e.getSlotId()) != 1)
      throw BusinessException.conflict("候补容量异常");
  }

  private void notify(WaitlistEntryDO e, String kind, String text) {
    var n = new NotificationEventDO();
    n.setTenantId(e.getTenantId());
    n.setUserId(e.getUserId());
    n.setEventKey("wait-" + e.getId() + "-" + kind);
    n.setMessage(text);
    n.setDelivered(false);
    n.setCreatedAt(now());
    events.insert(n);
  }

  @Transactional
  public void reconcile(long tenant, long slotId) {
    // 所有普通占号、取消和候补均先锁机构行，保持同一锁顺序。
    tenants.lock(tenant);
    var slot =
        slots.selectOne(
            Wrappers.<SlotDO>lambdaQuery()
                .eq(SlotDO::getTenantId, tenant)
                .eq(SlotDO::getId, slotId));
    if (slot == null) return;
    var queue = entries.selectList(active(tenant, slotId).orderByAsc(WaitlistEntryDO::getId));
    boolean opened = open(slot);
    for (var e : queue) {
      if (!opened || ("OFFERED".equals(e.getStatus()) && !e.getExpiresAt().isAfter(now()))) {
        if ("OFFERED".equals(e.getStatus())) release(e);
        e.setStatus(opened ? "EXPIRED" : "CLOSED");
        entries.updateById(e);
        notify(e, "closed", opened ? "候补保留超时，已释放号源" : "排班已关闭，候补已终止");
      }
    }
    if (!opened) return;
    for (var e : queue) {
      if (!"WAITING".equals(e.getStatus())) continue;
      // 授权撤销或账号停用后不继续分配；重新确认时仍会再次校验档案。
      // 候补分配只检查资格，不调用会把业务异常标记为rollback-only的授权事务。
      // 真正确认预约时仍调用requireOwned并锁档案，防止撤销授权与确认并发。
      boolean eligible =
          accounts.exists(
                  Wrappers.<AccountDO>lambdaQuery()
                      .eq(AccountDO::getTenantId, tenant)
                      .eq(AccountDO::getId, e.getUserId())
                      .eq(AccountDO::getEnabled, true))
              && patients.exists(
                  Wrappers.<PatientProfileDO>lambdaQuery()
                      .eq(PatientProfileDO::getTenantId, tenant)
                      .eq(PatientProfileDO::getId, e.getPatientId())
                      .eq(PatientProfileDO::getOwnerId, e.getUserId())
                      .eq(PatientProfileDO::getAuthorized, true));
      if (!eligible) {
        e.setStatus("CLOSED");
        entries.updateById(e);
        notify(e, "closed", "候补授权失效，请核对就诊人档案");
        continue;
      }
      if (slots.reserve(tenant, slotId) != 1) break;
      e.setStatus("OFFERED");
      e.setExpiresAt(
          now().plusMinutes(10).isBefore(slot.getStartAt())
              ? now().plusMinutes(10)
              : slot.getStartAt());
      entries.updateById(e);
      notify(e, "offered", "候补 #" + e.getId() + " 已获得号源，请在 " + e.getExpiresAt() + " 前确认");
    }
  }

  public synchronized void poll() {
    var tx = new TransactionTemplate(transactions);
    // 每轮按机构/号源去重处理；每次事务提交后即可安全重启。
    var pending =
        entries
            .selectPage(
                new Page<>(1, 200),
                Wrappers.<WaitlistEntryDO>lambdaQuery()
                    .in(WaitlistEntryDO::getStatus, List.of("WAITING", "OFFERED"))
                    .gt(WaitlistEntryDO::getId, cursor)
                    .orderByAsc(WaitlistEntryDO::getId))
            .getRecords();
    cursor = pending.isEmpty() ? 0 : pending.getLast().getId();
    var visited = new HashSet<String>();
    for (var e : pending) {
      if (!visited.add(e.getTenantId() + ":" + e.getSlotId())) continue;
      try {
        tx.executeWithoutResult(s -> reconcile(e.getTenantId(), e.getSlotId()));
      } catch (RuntimeException ex) {
        log.warn("候补处理失败，条目{}，类型{}", e.getId(), ex.getClass().getSimpleName());
      }
    }
    var outbox =
        events
            .selectPage(
                new Page<>(1, 100),
                Wrappers.<NotificationEventDO>lambdaQuery()
                    .eq(NotificationEventDO::getDelivered, false)
                    .orderByAsc(NotificationEventDO::getId))
            .getRecords();
    for (var event : outbox) {
      try {
        tx.executeWithoutResult(
            s -> {
              tenants.lock(event.getTenantId());
              var n = events.selectById(event.getId());
              if (Boolean.TRUE.equals(n.getDelivered())) return;
              // 收件箱唯一event_id防重，消息写入和投递标记同事务。
              if (!messages.exists(
                  Wrappers.<InboxMessageDO>lambdaQuery()
                      .eq(InboxMessageDO::getEventId, n.getId()))) {
                var m = new InboxMessageDO();
                m.setTenantId(n.getTenantId());
                m.setUserId(n.getUserId());
                m.setEventId(n.getId());
                m.setMessage(n.getMessage());
                m.setCreatedAt(n.getCreatedAt());
                messages.insert(m);
              }
              n.setDelivered(true);
              events.updateById(n);
            });
      } catch (RuntimeException ex) {
        log.warn("通知投递失败，事件{}，类型{}", event.getId(), ex.getClass().getSimpleName());
      }
    }
  }

  public List<InboxMessageDO> inbox(SessionUser u) {
    return messages
        .selectPage(
            new Page<>(1, 100),
            Wrappers.<InboxMessageDO>lambdaQuery()
                .eq(InboxMessageDO::getTenantId, u.tenantId())
                .eq(InboxMessageDO::getUserId, u.id())
                .orderByDesc(InboxMessageDO::getId))
        .getRecords();
  }

  @Transactional
  public void read(SessionUser u, long id) {
    messages.update(
        Wrappers.<InboxMessageDO>lambdaUpdate()
            .eq(InboxMessageDO::getTenantId, u.tenantId())
            .eq(InboxMessageDO::getUserId, u.id())
            .eq(InboxMessageDO::getId, id)
            .isNull(InboxMessageDO::getReadAt)
            .set(InboxMessageDO::getReadAt, now()));
  }

  public com.yiyue.model.resp.WaitlistResp detail(SessionUser u, long id) {
    var e = own(u, id);
    var slot = slots.selectById(e.getSlotId());
    var schedule = schedules.selectById(slot.getScheduleId());
    return new com.yiyue.model.resp.WaitlistResp(
        e.getId(),
        e.getSlotId(),
        e.getPatientId(),
        e.getStatus(),
        e.getExpiresAt(),
        e.getAppointmentId(),
        slot.getStartAt(),
        slot.getEndAt(),
        doctors.selectById(schedule.getDoctorId()).getName(),
        campuses.selectById(schedule.getCampusId()).getName(),
        patients.selectById(e.getPatientId()).getName());
  }

  private void audit(SessionUser u, String action, long id) {
    var a = new AuditEventDO();
    a.setTenantId(u.tenantId());
    a.setActorId(u.id());
    a.setAction(action);
    a.setTargetId(id);
    a.setCreatedAt(now());
    audits.insert(a);
  }
}
