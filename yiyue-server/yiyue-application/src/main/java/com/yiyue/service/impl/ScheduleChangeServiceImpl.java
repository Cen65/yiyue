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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ScheduleChangeServiceImpl implements ScheduleChangeService {
  private final ScheduleMapper schedules;
  private final SlotMapper slots;
  private final AppointmentMapper appointments;
  private final TenantMapper tenants;
  private final ScheduleChangeMapper changes;
  private final ScheduleChangeItemMapper items;
  private final AuditEventMapper audits;
  private final ScheduleService scheduleService;
  private final BookingService bookings;
  private final ChangeItemProcessor processor;
  private final Clock clock;

  private void requireAdmin(SessionUser u) {
    if (!"ADMIN".equals(u.role())) throw new BusinessException(HttpStatus.FORBIDDEN, "仅机构管理员可变更排班");
  }

  private ScheduleChangeDO owned(SessionUser u, long id) {
    var c =
        changes.selectOne(
            Wrappers.<ScheduleChangeDO>lambdaQuery()
                .eq(ScheduleChangeDO::getTenantId, u.tenantId())
                .eq(ScheduleChangeDO::getId, id));
    if (c == null) throw BusinessException.missing();
    return c;
  }

  @Transactional
  public ScheduleChangeResp create(SessionUser u, long scheduleId, ScheduleChangeReq r) {
    requireAdmin(u);
    tenants.lock(u.tenantId());
    var existing =
        changes.selectOne(
            Wrappers.<ScheduleChangeDO>lambdaQuery()
                .eq(ScheduleChangeDO::getTenantId, u.tenantId())
                .eq(ScheduleChangeDO::getScheduleId, scheduleId));
    if (existing != null) {
      if (!existing.getKind().equals(r.kind())) throw BusinessException.conflict("该排班已按其他方式变更");
      if ("SUBSTITUTE".equals(r.kind())
          && !Objects.equals(
              schedules.selectById(existing.getReplacementScheduleId()).getDoctorId(),
              r.replacementDoctorId())) throw BusinessException.conflict("该排班已有不同替诊医生");
      return summary(existing);
    }
    var schedule =
        schedules.selectOne(
            Wrappers.<ScheduleDO>lambdaQuery()
                .eq(ScheduleDO::getTenantId, u.tenantId())
                .eq(ScheduleDO::getId, scheduleId));
    if (schedule == null) throw BusinessException.missing();
    if (!"OPEN".equals(schedule.getStatus())
        || !schedule.getStartAt().isAfter(LocalDateTime.now(clock)))
      throw BusinessException.conflict("仅可变更尚未开始的开放排班");
    var sourceSlots =
        slots.selectList(
            Wrappers.<SlotDO>lambdaQuery()
                .eq(SlotDO::getTenantId, u.tenantId())
                .eq(SlotDO::getScheduleId, scheduleId)
                .orderByAsc(SlotDO::getStartAt));
    if (sourceSlots.isEmpty()) throw BusinessException.conflict("排班缺少号源");
    var slotIds = sourceSlots.stream().map(SlotDO::getId).toList();
    if (appointments.exists(
        Wrappers.<AppointmentDO>lambdaQuery()
            .eq(AppointmentDO::getTenantId, u.tenantId())
            .in(AppointmentDO::getSlotId, slotIds)
            .eq(AppointmentDO::getStatus, "CHECKED_IN")))
      throw BusinessException.conflict("排班存在已签到患者，请先处理现场接诊");
    var affected =
        appointments.selectList(
            Wrappers.<AppointmentDO>lambdaQuery()
                .eq(AppointmentDO::getTenantId, u.tenantId())
                .in(AppointmentDO::getSlotId, slotIds)
                .eq(AppointmentDO::getStatus, "BOOKED"));
    Long replacement = null;
    if ("SUBSTITUTE".equals(r.kind())) {
      if (r.replacementDoctorId() == null || r.replacementDoctorId().equals(schedule.getDoctorId()))
        throw BusinessException.conflict("请选择其他替诊医生");
      var first = sourceSlots.getFirst();
      int minutes = (int) Duration.between(first.getStartAt(), first.getEndAt()).toMinutes();
      if (sourceSlots.stream()
          .anyMatch(
              s ->
                  s.getCapacity().intValue() != first.getCapacity().intValue()
                      || Duration.between(s.getStartAt(), s.getEndAt()).toMinutes() != minutes))
        throw BusinessException.conflict("非均匀排班请先另行发布替诊排班再提供改约建议");
      replacement =
          scheduleService
              .create(
                  u,
                  new ScheduleCreateReq(
                      schedule.getCampusId(),
                      r.replacementDoctorId(),
                      schedule.getStartAt(),
                      schedule.getEndAt(),
                      minutes,
                      first.getCapacity(),
                      schedule.getDepartmentId()))
              .getId();
    } else if (!"STOP".equals(r.kind())) throw BusinessException.conflict("不支持的变更方式");
    // 停止新预约与影响清单一次提交；逐单处理由持久化任务恢复，不能只修改排班状态。
    schedules.update(
        Wrappers.<ScheduleDO>lambdaUpdate()
            .eq(ScheduleDO::getTenantId, u.tenantId())
            .eq(ScheduleDO::getId, scheduleId)
            .set(ScheduleDO::getStatus, "STOPPED")
            .set(ScheduleDO::getStopReason, r.reason().strip()));
    var change = new ScheduleChangeDO();
    change.setTenantId(u.tenantId());
    change.setScheduleId(scheduleId);
    change.setReplacementScheduleId(replacement);
    change.setKind(r.kind());
    change.setReason(r.reason().strip());
    change.setStatus(affected.isEmpty() ? "DONE" : "PROCESSING");
    change.setActorId(u.id());
    change.setCreatedAt(LocalDateTime.now(clock));
    changes.insert(change);
    for (var a : affected) {
      var item = new ScheduleChangeItemDO();
      item.setTenantId(u.tenantId());
      item.setChangeId(change.getId());
      item.setAppointmentId(a.getId());
      item.setTargetScheduleId(replacement);
      item.setStatus("PENDING");
      item.setDecision("PENDING");
      item.setAttempts(0);
      items.insert(item);
    }
    audit(u, "SCHEDULE_" + r.kind(), change.getId());
    return summary(change);
  }

  private com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ScheduleChangeItemDO>
      itemQuery(ScheduleChangeDO c) {
    return Wrappers.<ScheduleChangeItemDO>lambdaQuery()
        .eq(ScheduleChangeItemDO::getTenantId, c.getTenantId())
        .eq(ScheduleChangeItemDO::getChangeId, c.getId());
  }

  private ScheduleChangeResp summary(ScheduleChangeDO c) {
    var source = schedules.selectById(c.getScheduleId());
    return new ScheduleChangeResp(
        c.getId(),
        c.getScheduleId(),
        c.getReplacementScheduleId(),
        c.getKind(),
        c.getReason(),
        c.getStatus(),
        c.getCreatedAt(),
        items.selectCount(itemQuery(c)),
        items.selectCount(itemQuery(c).eq(ScheduleChangeItemDO::getStatus, "DONE")),
        items.selectCount(itemQuery(c).eq(ScheduleChangeItemDO::getStatus, "FAILED")),
        source.getCampusId(),
        source.getDepartmentId());
  }

  public List<ScheduleChangeResp> list(SessionUser u) {
    requireAdmin(u);
    return changes
        .selectPage(
            new Page<>(1, 100),
            Wrappers.<ScheduleChangeDO>lambdaQuery()
                .eq(ScheduleChangeDO::getTenantId, u.tenantId())
                .orderByDesc(ScheduleChangeDO::getId))
        .getRecords()
        .stream()
        .map(this::summary)
        .toList();
  }

  public List<ChangeItemResp> items(SessionUser u, long id) {
    requireAdmin(u);
    var c = owned(u, id);
    return items
        .selectList(
            Wrappers.<ScheduleChangeItemDO>lambdaQuery()
                .eq(ScheduleChangeItemDO::getTenantId, u.tenantId())
                .eq(ScheduleChangeItemDO::getChangeId, id)
                .orderByAsc(ScheduleChangeItemDO::getId))
        .stream()
        .map(i -> view(i, c.getReason()))
        .toList();
  }

  private ChangeItemResp view(ScheduleChangeItemDO i, String reason) {
    return new ChangeItemResp(
        i.getId(),
        i.getChangeId(),
        i.getAppointmentId(),
        i.getTargetScheduleId(),
        i.getNewAppointmentId(),
        i.getStatus(),
        i.getDecision(),
        i.getAttempts(),
        i.getLastError(),
        reason);
  }

  public void process(SessionUser u, long id) {
    requireAdmin(u);
    var c = owned(u, id);
    var pending =
        items
            .selectPage(
                new Page<>(1, 20),
                Wrappers.<ScheduleChangeItemDO>lambdaQuery()
                    .eq(ScheduleChangeItemDO::getTenantId, u.tenantId())
                    .eq(ScheduleChangeItemDO::getChangeId, id)
                    .eq(ScheduleChangeItemDO::getStatus, "PENDING")
                    .orderByAsc(ScheduleChangeItemDO::getId))
            .getRecords();
    for (var i : pending) processor.process(c, i.getId());
    processor.refresh(c);
  }

  @Transactional
  public void retry(SessionUser u, long id) {
    requireAdmin(u);
    tenants.lock(u.tenantId());
    var change = owned(u, id);
    if (!items.exists(itemQuery(change).eq(ScheduleChangeItemDO::getStatus, "FAILED")))
      throw BusinessException.conflict("没有可重试的失败项");
    items.update(
        Wrappers.<ScheduleChangeItemDO>lambdaUpdate()
            .eq(ScheduleChangeItemDO::getTenantId, u.tenantId())
            .eq(ScheduleChangeItemDO::getChangeId, id)
            .eq(ScheduleChangeItemDO::getStatus, "FAILED")
            .set(ScheduleChangeItemDO::getStatus, "PENDING")
            .set(ScheduleChangeItemDO::getAttempts, 0)
            .set(ScheduleChangeItemDO::getLastError, null));
    changes.update(
        Wrappers.<ScheduleChangeDO>lambdaUpdate()
            .eq(ScheduleChangeDO::getTenantId, u.tenantId())
            .eq(ScheduleChangeDO::getId, id)
            .set(ScheduleChangeDO::getStatus, "PROCESSING"));
    audit(u, "CHANGE_RETRY", id);
  }

  @Transactional
  public void propose(SessionUser u, long itemId, long targetScheduleId) {
    requireAdmin(u);
    tenants.lock(u.tenantId());
    var i = item(u, itemId);
    if (!"PENDING".equals(i.getDecision())) throw BusinessException.conflict("患者已作出决定，不能覆盖");
    validateTarget(u, i, targetScheduleId);
    items.update(
        Wrappers.<ScheduleChangeItemDO>lambdaUpdate()
            .eq(ScheduleChangeItemDO::getTenantId, u.tenantId())
            .eq(ScheduleChangeItemDO::getId, itemId)
            .set(ScheduleChangeItemDO::getTargetScheduleId, targetScheduleId));
    audit(u, "CHANGE_PROPOSE", itemId);
  }

  private ScheduleChangeItemDO item(SessionUser u, long id) {
    var i =
        items.selectOne(
            Wrappers.<ScheduleChangeItemDO>lambdaQuery()
                .eq(ScheduleChangeItemDO::getTenantId, u.tenantId())
                .eq(ScheduleChangeItemDO::getId, id));
    if (i == null) throw BusinessException.missing();
    return i;
  }

  private AppointmentDO patientOwn(SessionUser u, ScheduleChangeItemDO i) {
    var a =
        appointments.selectOne(
            Wrappers.<AppointmentDO>lambdaQuery()
                .eq(AppointmentDO::getTenantId, u.tenantId())
                .eq(AppointmentDO::getId, i.getAppointmentId())
                .eq(AppointmentDO::getUserId, u.id()));
    if (a == null) throw BusinessException.missing();
    return a;
  }

  private void validateTarget(SessionUser u, ScheduleChangeItemDO i, long id) {
    var c = owned(u, i.getChangeId());
    var source = schedules.selectById(c.getScheduleId());
    var target =
        schedules.selectOne(
            Wrappers.<ScheduleDO>lambdaQuery()
                .eq(ScheduleDO::getTenantId, u.tenantId())
                .eq(ScheduleDO::getId, id)
                .eq(ScheduleDO::getStatus, "OPEN"));
    if (target == null
        || !target.getStartAt().isAfter(LocalDateTime.now(clock))
        || !source.getDepartmentId().equals(target.getDepartmentId())
        || !source.getCampusId().equals(target.getCampusId()))
      throw BusinessException.conflict("仅可建议本机构同院区同科室的未来开放排班");
  }

  public List<ChangeItemResp> mine(SessionUser u) {
    var owned =
        appointments.selectList(
            Wrappers.<AppointmentDO>lambdaQuery()
                .eq(AppointmentDO::getTenantId, u.tenantId())
                .eq(AppointmentDO::getUserId, u.id()));
    if (owned.isEmpty()) return List.of();
    return items
        .selectPage(
            new Page<>(1, 100),
            Wrappers.<ScheduleChangeItemDO>lambdaQuery()
                .eq(ScheduleChangeItemDO::getTenantId, u.tenantId())
                .in(
                    ScheduleChangeItemDO::getAppointmentId,
                    owned.stream().map(AppointmentDO::getId).toList())
                .orderByDesc(ScheduleChangeItemDO::getId))
        .getRecords()
        .stream()
        .map(i -> view(i, this.owned(u, i.getChangeId()).getReason()))
        .toList();
  }

  @Transactional
  public AppointmentResp accept(SessionUser u, long id, long slotId) {
    tenants.lock(u.tenantId());
    var i = item(u, id);
    var old = patientOwn(u, i);
    if ("ACCEPTED".equals(i.getDecision())) {
      var existing = appointments.selectById(i.getNewAppointmentId());
      if (!existing.getSlotId().equals(slotId)) throw BusinessException.conflict("已确认其他时段");
      return AppointmentResp.from(existing);
    }
    if (!"PENDING".equals(i.getDecision())
        || !"DONE".equals(i.getStatus())
        || !"CANCELLED".equals(old.getStatus())
        || i.getTargetScheduleId() == null) throw BusinessException.conflict("当前不能确认改约");
    validateTarget(u, i, i.getTargetScheduleId());
    var slot =
        slots.selectOne(
            Wrappers.<SlotDO>lambdaQuery()
                .eq(SlotDO::getTenantId, u.tenantId())
                .eq(SlotDO::getId, slotId)
                .eq(SlotDO::getScheduleId, i.getTargetScheduleId()));
    if (slot == null) throw BusinessException.missing();
    // 患者明确确认后才占用新号源，复用原预约幂等/容量/档案授权校验，不自动强制迁移。
    var created = bookings.book(u, slotId, "change-" + i.getId(), old.getPatientId());
    items.update(
        Wrappers.<ScheduleChangeItemDO>lambdaUpdate()
            .eq(ScheduleChangeItemDO::getTenantId, u.tenantId())
            .eq(ScheduleChangeItemDO::getId, id)
            .set(ScheduleChangeItemDO::getDecision, "ACCEPTED")
            .set(ScheduleChangeItemDO::getNewAppointmentId, created.getId()));
    audit(u, "CHANGE_ACCEPT", id);
    return AppointmentResp.from(created);
  }

  @Transactional
  public void decline(SessionUser u, long id) {
    tenants.lock(u.tenantId());
    var i = item(u, id);
    patientOwn(u, i);
    if ("DECLINED".equals(i.getDecision())) return;
    if (!"PENDING".equals(i.getDecision())) throw BusinessException.conflict("已确认的改约不能撤销此决定");
    items.update(
        Wrappers.<ScheduleChangeItemDO>lambdaUpdate()
            .eq(ScheduleChangeItemDO::getTenantId, u.tenantId())
            .eq(ScheduleChangeItemDO::getId, id)
            .set(ScheduleChangeItemDO::getDecision, "DECLINED"));
    audit(u, "CHANGE_DECLINE", id);
  }

  private void audit(SessionUser u, String action, long id) {
    var e = new AuditEventDO();
    e.setTenantId(u.tenantId());
    e.setActorId(u.id());
    e.setAction(action);
    e.setTargetId(id);
    e.setCreatedAt(LocalDateTime.now(clock));
    audits.insert(e);
  }
}
