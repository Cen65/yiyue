package com.yiyue.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yiyue.auth.SessionUser;
import com.yiyue.common.BusinessException;
import com.yiyue.mapper.AppointmentMapper;
import com.yiyue.mapper.AuditEventMapper;
import com.yiyue.mapper.SlotMapper;
import com.yiyue.model.entity.AppointmentDO;
import com.yiyue.model.entity.AuditEventDO;
import com.yiyue.model.entity.SlotDO;
import com.yiyue.service.BookingService;
import com.yiyue.service.PatientProfileService;
import com.yiyue.service.StaffAccessService;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {
  private final SlotMapper slots;
  private final com.yiyue.service.WaitlistService waitlist;
  private final AppointmentMapper appointments;
  private final AuditEventMapper audits;
  private final Clock clock;
  private final PatientProfileService profiles;
  private final StaffAccessService staffAccess;
  private final com.yiyue.mapper.TenantMapper tenants;
  private final com.yiyue.mapper.ScheduleMapper schedules;

  @Transactional
  public AppointmentDO book(SessionUser user, long slotId, String key) {
    return book(user, slotId, key, null);
  }

  @Transactional
  public AppointmentDO book(SessionUser user, long slotId, String key, Long patientId) {
    tenants.lock(user.tenantId());
    var patient = profiles.requireOwned(user, patientId);
    AppointmentDO existing =
        appointments.selectOne(
            Wrappers.<AppointmentDO>lambdaQuery()
                .eq(AppointmentDO::getTenantId, user.tenantId())
                .eq(AppointmentDO::getUserId, user.id())
                .eq(AppointmentDO::getRequestKey, key));
    if (existing != null) {
      if (!existing.getSlotId().equals(slotId)
          || !java.util.Objects.equals(existing.getPatientId(), patient.getId()))
        throw BusinessException.conflict("请求标识已用于其他号源");
      return existing;
    }
    SlotDO slot =
        slots.selectOne(
            Wrappers.<SlotDO>lambdaQuery()
                .eq(SlotDO::getTenantId, user.tenantId())
                .eq(SlotDO::getId, slotId));
    if (slot == null) throw BusinessException.missing();
    var schedule =
        schedules.selectOne(
            Wrappers.<com.yiyue.model.entity.ScheduleDO>lambdaQuery()
                .eq(com.yiyue.model.entity.ScheduleDO::getTenantId, user.tenantId())
                .eq(com.yiyue.model.entity.ScheduleDO::getId, slot.getScheduleId())
                .eq(com.yiyue.model.entity.ScheduleDO::getStatus, "OPEN"));
    if (schedule == null) throw BusinessException.conflict("排班已停诊，请查看变更通知");
    if (!slot.getStartAt().isAfter(LocalDateTime.now(clock)))
      throw BusinessException.conflict("该时段已开始，无法预约");
    // 数据库原子占位与预约落库共用事务；重复请求的唯一键冲突会回滚占用。
    waitlist.reconcile(user.tenantId(), slotId);
    if (slots.reserve(user.tenantId(), slotId) != 1)
      throw BusinessException.conflict("号源已满，请选择其他时段");
    AppointmentDO appointment = new AppointmentDO();
    appointment.setTenantId(user.tenantId());
    appointment.setUserId(user.id());
    appointment.setSlotId(slotId);
    appointment.setPatientId(patient.getId());
    appointment.setRequestKey(key);
    appointment.setStatus("BOOKED");
    appointment.setCreatedAt(LocalDateTime.now(clock));
    appointments.insert(appointment);
    audit(user, "BOOK", appointment.getId());
    return appointment;
  }

  @Transactional
  public void cancel(SessionUser user, long id) {
    tenants.lock(user.tenantId());
    AppointmentDO appointment = own(user, id);
    if ("CANCELLED".equals(appointment.getStatus())) return;
    SlotDO slot = slots.selectById(appointment.getSlotId());
    if (!slot.getStartAt().isAfter(LocalDateTime.now(clock)))
      throw BusinessException.conflict("时段已开始，无法取消");
    // 带原状态条件的迁移保证重复取消只释放一次号源。
    int changed =
        appointments.update(
            Wrappers.<AppointmentDO>lambdaUpdate()
                .eq(AppointmentDO::getId, id)
                .eq(AppointmentDO::getTenantId, user.tenantId())
                .eq(AppointmentDO::getUserId, user.id())
                .eq(AppointmentDO::getStatus, "BOOKED")
                .set(AppointmentDO::getStatus, "CANCELLED"));
    if (changed == 0) throw BusinessException.conflict("预约状态已变化，请刷新");
    if (slots.release(user.tenantId(), appointment.getSlotId()) != 1)
      throw BusinessException.conflict("号源数据异常，请联系管理员");
    waitlist.reconcile(user.tenantId(), appointment.getSlotId());
    audit(user, "CANCEL", id);
  }

  @Transactional
  public void transition(SessionUser user, long id, String action) {
    tenants.lock(user.tenantId());
    staffAccess.checkTransition(user, id, action);
    var current =
        appointments.selectOne(
            Wrappers.<AppointmentDO>lambdaQuery()
                .eq(AppointmentDO::getTenantId, user.tenantId())
                .eq(AppointmentDO::getId, id));
    if (current == null) throw BusinessException.missing();
    var slot = slots.selectById(current.getSlotId());
    var schedule = schedules.selectById(slot.getScheduleId());
    if (!"OPEN".equals(schedule.getStatus())) throw BusinessException.conflict("排班已停诊，不能办理接诊");
    String from, to;
    switch (action) {
      case "check-in" -> {
        from = "BOOKED";
        to = "CHECKED_IN";
      }
      case "complete" -> {
        from = "CHECKED_IN";
        to = "COMPLETED";
      }
      default -> throw BusinessException.conflict("不支持的操作");
    }
    int changed =
        appointments.update(
            Wrappers.<AppointmentDO>lambdaUpdate()
                .eq(AppointmentDO::getTenantId, user.tenantId())
                .eq(AppointmentDO::getId, id)
                .eq(AppointmentDO::getStatus, from)
                .set(AppointmentDO::getStatus, to));
    if (changed == 0) throw BusinessException.conflict("预约不存在或当前状态不允许操作");
    audit(user, action, id);
  }

  private AppointmentDO own(SessionUser user, long id) {
    AppointmentDO a =
        appointments.selectOne(
            Wrappers.<AppointmentDO>lambdaQuery()
                .eq(AppointmentDO::getId, id)
                .eq(AppointmentDO::getTenantId, user.tenantId())
                .eq(AppointmentDO::getUserId, user.id()));
    if (a == null) throw BusinessException.missing();
    return a;
  }

  private void audit(SessionUser user, String action, long id) {
    AuditEventDO e = new AuditEventDO();
    e.setTenantId(user.tenantId());
    e.setActorId(user.id());
    e.setAction(action);
    e.setTargetId(id);
    e.setCreatedAt(LocalDateTime.now(clock));
    audits.insert(e);
  }
}
