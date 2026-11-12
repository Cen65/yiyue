package com.yiyue.booking;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yiyue.auth.SessionUser;
import com.yiyue.common.BusinessException;
import com.yiyue.domain.*;
import com.yiyue.mapper.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookingService {
  private final SlotMapper slots;
  private final AppointmentMapper appointments;
  private final AuditEventMapper audits;
  private final Clock clock;

  @Transactional
  public Appointment book(SessionUser user, long slotId, String key) {
    Appointment existing =
        appointments.selectOne(
            Wrappers.<Appointment>lambdaQuery()
                .eq(Appointment::getTenantId, user.tenantId())
                .eq(Appointment::getUserId, user.id())
                .eq(Appointment::getRequestKey, key));
    if (existing != null) {
      if (!existing.getSlotId().equals(slotId)) throw BusinessException.conflict("请求标识已用于其他号源");
      return existing;
    }
    Slot slot =
        slots.selectOne(
            Wrappers.<Slot>lambdaQuery()
                .eq(Slot::getTenantId, user.tenantId())
                .eq(Slot::getId, slotId));
    if (slot == null) throw BusinessException.missing();
    if (!slot.getStartAt().isAfter(LocalDateTime.now(clock)))
      throw BusinessException.conflict("该时段已开始，无法预约");
    // 数据库原子占位与预约落库共用事务；重复请求的唯一键冲突会回滚占用。
    if (slots.reserve(user.tenantId(), slotId) != 1)
      throw BusinessException.conflict("号源已满，请选择其他时段");
    Appointment appointment = new Appointment();
    appointment.setTenantId(user.tenantId());
    appointment.setUserId(user.id());
    appointment.setSlotId(slotId);
    appointment.setRequestKey(key);
    appointment.setStatus("BOOKED");
    appointment.setCreatedAt(LocalDateTime.now(clock));
    appointments.insert(appointment);
    audit(user, "BOOK", appointment.getId());
    return appointment;
  }

  @Transactional
  public void cancel(SessionUser user, long id) {
    Appointment appointment = own(user, id);
    if ("CANCELLED".equals(appointment.getStatus())) return;
    Slot slot = slots.selectById(appointment.getSlotId());
    if (!slot.getStartAt().isAfter(LocalDateTime.now(clock)))
      throw BusinessException.conflict("时段已开始，无法取消");
    // 带原状态条件的迁移保证重复取消只释放一次号源。
    int changed =
        appointments.update(
            Wrappers.<Appointment>lambdaUpdate()
                .eq(Appointment::getId, id)
                .eq(Appointment::getTenantId, user.tenantId())
                .eq(Appointment::getUserId, user.id())
                .eq(Appointment::getStatus, "BOOKED")
                .set(Appointment::getStatus, "CANCELLED"));
    if (changed == 0) throw BusinessException.conflict("预约状态已变化，请刷新");
    if (slots.release(user.tenantId(), appointment.getSlotId()) != 1)
      throw BusinessException.conflict("号源数据异常，请联系管理员");
    audit(user, "CANCEL", id);
  }

  @Transactional
  public void transition(SessionUser user, long id, String action) {
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
            Wrappers.<Appointment>lambdaUpdate()
                .eq(Appointment::getTenantId, user.tenantId())
                .eq(Appointment::getId, id)
                .eq(Appointment::getStatus, from)
                .set(Appointment::getStatus, to));
    if (changed == 0) throw BusinessException.conflict("预约不存在或当前状态不允许操作");
    audit(user, action, id);
  }

  private Appointment own(SessionUser user, long id) {
    Appointment a =
        appointments.selectOne(
            Wrappers.<Appointment>lambdaQuery()
                .eq(Appointment::getId, id)
                .eq(Appointment::getTenantId, user.tenantId())
                .eq(Appointment::getUserId, user.id()));
    if (a == null) throw BusinessException.missing();
    return a;
  }

  private void audit(SessionUser user, String action, long id) {
    AuditEvent e = new AuditEvent();
    e.setTenantId(user.tenantId());
    e.setActorId(user.id());
    e.setAction(action);
    e.setTargetId(id);
    e.setCreatedAt(LocalDateTime.now(clock));
    audits.insert(e);
  }
}
