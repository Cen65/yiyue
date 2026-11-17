package com.yiyue.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yiyue.common.BusinessException;
import com.yiyue.mapper.*;
import com.yiyue.model.entity.*;
import java.time.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@RequiredArgsConstructor
public class ChangeItemProcessor {
  private static final int MAX_ATTEMPTS = 3;
  private final TenantMapper tenants;
  private final ScheduleChangeMapper changes;
  private final ScheduleChangeItemMapper items;
  private final AppointmentMapper appointments;
  private final SlotMapper slots;
  private final AuditEventMapper audits;
  private final PlatformTransactionManager transactions;
  private final Clock clock;

  public void process(ScheduleChangeDO change, long itemId) {
    var tx = new TransactionTemplate(transactions);
    try {
      tx.executeWithoutResult(
          status -> {
            tenants.lock(change.getTenantId());
            var item =
                items.selectOne(
                    Wrappers.<ScheduleChangeItemDO>lambdaQuery()
                        .eq(ScheduleChangeItemDO::getTenantId, change.getTenantId())
                        .eq(ScheduleChangeItemDO::getChangeId, change.getId())
                        .eq(ScheduleChangeItemDO::getId, itemId));
            if (item == null || !"PENDING".equals(item.getStatus())) return;
            var a =
                appointments.selectOne(
                    Wrappers.<AppointmentDO>lambdaQuery()
                        .eq(AppointmentDO::getTenantId, change.getTenantId())
                        .eq(AppointmentDO::getId, item.getAppointmentId()));
            if (a == null) throw BusinessException.missing();
            if ("BOOKED".equals(a.getStatus())) {
              int updated =
                  appointments.update(
                      Wrappers.<AppointmentDO>lambdaUpdate()
                          .eq(AppointmentDO::getTenantId, change.getTenantId())
                          .eq(AppointmentDO::getId, a.getId())
                          .eq(AppointmentDO::getStatus, "BOOKED")
                          .set(AppointmentDO::getStatus, "CANCELLED"));
              if (updated != 1 || slots.release(change.getTenantId(), a.getSlotId()) != 1)
                throw BusinessException.conflict("号源或预约状态异常");
            } else if (!"CANCELLED".equals(a.getStatus()))
              throw BusinessException.conflict("预约已进入接诊流程");
            item.setStatus("DONE");
            item.setAttempts(item.getAttempts() + 1);
            item.setLastError(null);
            items.updateById(item);
            var e = new AuditEventDO();
            e.setTenantId(change.getTenantId());
            e.setActorId(change.getActorId());
            e.setAction("CHANGE_CANCEL");
            e.setTargetId(a.getId());
            e.setCreatedAt(LocalDateTime.now(clock));
            audits.insert(e);
          });
    } catch (RuntimeException ex) {
      // 业务事务失败后另开事务记录失败；原容量与预约更新不能因错误记录而半提交。
      tx.executeWithoutResult(
          status -> {
            tenants.lock(change.getTenantId());
            var item =
                items.selectOne(
                    Wrappers.<ScheduleChangeItemDO>lambdaQuery()
                        .eq(ScheduleChangeItemDO::getTenantId, change.getTenantId())
                        .eq(ScheduleChangeItemDO::getId, itemId));
            if (item == null || !"PENDING".equals(item.getStatus())) return;
            item.setAttempts(item.getAttempts() + 1);
            item.setStatus(item.getAttempts() >= MAX_ATTEMPTS ? "FAILED" : "PENDING");
            item.setLastError(ex instanceof BusinessException ? ex.getMessage() : "系统暂时不可用，请稍后重试");
            items.updateById(item);
          });
    }
  }

  public void refresh(ScheduleChangeDO change) {
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            status -> {
              tenants.lock(change.getTenantId());
              var pending =
                  items.exists(
                      Wrappers.<ScheduleChangeItemDO>lambdaQuery()
                          .eq(ScheduleChangeItemDO::getTenantId, change.getTenantId())
                          .eq(ScheduleChangeItemDO::getChangeId, change.getId())
                          .eq(ScheduleChangeItemDO::getStatus, "PENDING"));
              var failed =
                  items.exists(
                      Wrappers.<ScheduleChangeItemDO>lambdaQuery()
                          .eq(ScheduleChangeItemDO::getTenantId, change.getTenantId())
                          .eq(ScheduleChangeItemDO::getChangeId, change.getId())
                          .eq(ScheduleChangeItemDO::getStatus, "FAILED"));
              changes.update(
                  Wrappers.<ScheduleChangeDO>lambdaUpdate()
                      .eq(ScheduleChangeDO::getTenantId, change.getTenantId())
                      .eq(ScheduleChangeDO::getId, change.getId())
                      .set(
                          ScheduleChangeDO::getStatus,
                          pending ? "PROCESSING" : failed ? "FAILED" : "DONE"));
            });
  }
}
