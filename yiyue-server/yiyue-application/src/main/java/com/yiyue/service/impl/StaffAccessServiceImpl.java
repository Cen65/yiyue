package com.yiyue.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yiyue.auth.SessionUser;
import com.yiyue.common.BusinessException;
import com.yiyue.mapper.*;
import com.yiyue.model.entity.*;
import com.yiyue.service.StaffAccessService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StaffAccessServiceImpl implements StaffAccessService {
  private final DoctorMapper doctors;
  private final AppointmentMapper appointments;
  private final SlotMapper slots;
  private final ScheduleMapper schedules;

  public List<Long> doctorIds(SessionUser u) {
    return doctors
        .selectList(
            Wrappers.<DoctorDO>lambdaQuery()
                .eq(DoctorDO::getTenantId, u.tenantId())
                .eq(DoctorDO::getAccountId, u.id())
                .eq(DoctorDO::getEnabled, true))
        .stream()
        .map(DoctorDO::getId)
        .toList();
  }

  public void checkTransition(SessionUser u, long id, String action) {
    if ("ADMIN".equals(u.role())) return;
    if ("RECEPTION".equals(u.role()) && "check-in".equals(action)) return;
    if (!"DOCTOR".equals(u.role()) || !"complete".equals(action))
      throw new BusinessException(HttpStatus.FORBIDDEN, "当前岗位无权执行该操作");
    var a =
        appointments.selectOne(
            Wrappers.<AppointmentDO>lambdaQuery()
                .eq(AppointmentDO::getTenantId, u.tenantId())
                .eq(AppointmentDO::getId, id));
    if (a == null) throw BusinessException.missing();
    var slot = slots.selectById(a.getSlotId());
    var schedule = schedules.selectById(slot.getScheduleId());
    if (!doctorIds(u).contains(schedule.getDoctorId()))
      throw new BusinessException(HttpStatus.FORBIDDEN, "不能操作其他医生的预约");
  }
}
