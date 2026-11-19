package com.yiyue.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yiyue.auth.SessionUser;
import com.yiyue.common.BusinessException;
import com.yiyue.mapper.*;
import com.yiyue.mapper.CampusMapper;
import com.yiyue.mapper.DoctorMapper;
import com.yiyue.mapper.ScheduleMapper;
import com.yiyue.mapper.SlotMapper;
import com.yiyue.model.entity.*;
import com.yiyue.model.entity.CampusDO;
import com.yiyue.model.entity.DoctorDO;
import com.yiyue.model.entity.ScheduleDO;
import com.yiyue.model.entity.SlotDO;
import com.yiyue.model.req.ScheduleCreateReq;
import com.yiyue.service.ScheduleService;
import jakarta.validation.constraints.*;
import java.time.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ScheduleServiceImpl implements ScheduleService {
  private final ScheduleMapper schedules;
  private final com.yiyue.service.ResourceService resources;
  private final SlotMapper slots;
  private final CampusMapper campuses;
  private final DoctorMapper doctors;
  private final Clock clock;
  private final TenantMapper tenants;
  private final DepartmentMapper departments;
  private final DoctorDepartmentMapper doctorDepartments;
  private final DoctorCampusMapper doctorCampuses;

  @Transactional
  public ScheduleDO create(SessionUser user, ScheduleCreateReq input) {
    if (input.endAt().isAfter(input.startAt().plusHours(12))
        || !input.endAt().isAfter(input.startAt())
        || !input.startAt().isAfter(LocalDateTime.now(clock))
        || !input.startAt().toLocalDate().equals(input.endAt().toLocalDate())
        || Duration.between(input.startAt(), input.endAt()).toSeconds()
                % (input.slotMinutes() * 60L)
            != 0) throw BusinessException.conflict("请选择未来同一天内、最长12小时且可按时段长度整分的排班");
    if (input.feeCents() != null && (input.feeCents() < 0 || input.feeCents() > 1000000))
      throw BusinessException.conflict("挂号费范围应为0至1000000分");
    tenants.lock(user.tenantId());
    CampusDO campus =
        campuses.selectOne(
            Wrappers.<CampusDO>lambdaQuery()
                .eq(CampusDO::getTenantId, user.tenantId())
                .eq(CampusDO::getId, input.campusId()));
    DoctorDO doctor =
        doctors.selectOne(
            Wrappers.<DoctorDO>lambdaQuery()
                .eq(DoctorDO::getTenantId, user.tenantId())
                .eq(DoctorDO::getId, input.doctorId()));
    if (campus == null || doctor == null) throw BusinessException.missing();
    if (!Boolean.TRUE.equals(campus.getEnabled()) || !Boolean.TRUE.equals(doctor.getEnabled()))
      throw BusinessException.conflict("院区或医生已停用");
    Long departmentId =
        input.departmentId() == null ? doctor.getDepartmentId() : input.departmentId();
    var department =
        departments.selectOne(
            Wrappers.<DepartmentDO>lambdaQuery()
                .eq(DepartmentDO::getTenantId, user.tenantId())
                .eq(DepartmentDO::getId, departmentId)
                .eq(DepartmentDO::getEnabled, true));
    if (department == null) throw BusinessException.conflict("科室不存在或已停用");
    var depLinks =
        doctorDepartments.selectList(
            Wrappers.<DoctorDepartmentDO>lambdaQuery()
                .eq(DoctorDepartmentDO::getTenantId, user.tenantId())
                .eq(DoctorDepartmentDO::getDoctorId, doctor.getId()));
    var campusLinks =
        doctorCampuses.selectList(
            Wrappers.<DoctorCampusDO>lambdaQuery()
                .eq(DoctorCampusDO::getTenantId, user.tenantId())
                .eq(DoctorCampusDO::getDoctorId, doctor.getId()));
    if ((!depLinks.isEmpty()
            && depLinks.stream().noneMatch(l -> l.getDepartmentId().equals(departmentId)))
        || (depLinks.isEmpty() && !doctor.getDepartmentId().equals(departmentId))
        || (!campusLinks.isEmpty()
            && campusLinks.stream().noneMatch(l -> l.getCampusId().equals(campus.getId()))))
      throw BusinessException.conflict("医生未关联该科室或院区");
    // 对同一医生串行校验排班，防止并发创建交叠时段（固定 XML 行锁）。
    doctors.lock(user.tenantId(), doctor.getId());
    boolean overlap =
        schedules.exists(
            Wrappers.<ScheduleDO>lambdaQuery()
                .eq(ScheduleDO::getTenantId, user.tenantId())
                .eq(ScheduleDO::getDoctorId, doctor.getId())
                .eq(ScheduleDO::getStatus, "OPEN")
                .lt(ScheduleDO::getStartAt, input.endAt())
                .gt(ScheduleDO::getEndAt, input.startAt()));
    if (overlap) throw BusinessException.conflict("该医生已有重叠排班，包括其他院区");
    ScheduleDO schedule = new ScheduleDO();
    schedule.setTenantId(user.tenantId());
    schedule.setCampusId(campus.getId());
    schedule.setDoctorId(doctor.getId());
    schedule.setDepartmentId(departmentId);
    schedule.setStartAt(input.startAt());
    schedule.setEndAt(input.endAt());
    schedule.setFeeCents(input.feeCents() == null ? 0 : input.feeCents());
    schedules.insert(schedule);
    resources.reserve(user, schedule, input.resourceIds());
    // 排班和时段一起提交，避免出现有排班却没有号源的半成品数据。
    for (LocalDateTime start = input.startAt();
        start.isBefore(input.endAt());
        start = start.plusMinutes(input.slotMinutes())) {
      SlotDO slot = new SlotDO();
      slot.setTenantId(user.tenantId());
      slot.setScheduleId(schedule.getId());
      slot.setStartAt(start);
      slot.setEndAt(start.plusMinutes(input.slotMinutes()));
      slot.setCapacity(input.capacity());
      slot.setReserved(0);
      slots.insert(slot);
    }
    return schedule;
  }
}
