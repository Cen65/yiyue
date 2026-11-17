package com.yiyue.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yiyue.auth.SessionUser;
import com.yiyue.mapper.CampusMapper;
import com.yiyue.mapper.DepartmentMapper;
import com.yiyue.mapper.DoctorMapper;
import com.yiyue.mapper.ScheduleMapper;
import com.yiyue.mapper.SlotMapper;
import com.yiyue.model.entity.CampusDO;
import com.yiyue.model.entity.DepartmentDO;
import com.yiyue.model.entity.DoctorDO;
import com.yiyue.model.entity.ScheduleDO;
import com.yiyue.model.entity.SlotDO;
import com.yiyue.model.resp.CampusResp;
import com.yiyue.model.resp.CatalogResp;
import com.yiyue.model.resp.DepartmentResp;
import com.yiyue.model.resp.DoctorResp;
import com.yiyue.service.CatalogService;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 按会话机构隔离查询，禁止使用前端传入的租户身份。 */
@Service
@RequiredArgsConstructor
public class CatalogServiceImpl implements CatalogService {
  private final CampusMapper campuses;
  private final DepartmentMapper departments;
  private final DoctorMapper doctors;
  private final ScheduleMapper schedules;
  private final SlotMapper slots;

  private final Clock clock;
  private final com.yiyue.service.StaffAccessService staffAccess;

  public CatalogResp catalog(SessionUser user) {
    return new CatalogResp(
        campuses
            .selectList(
                Wrappers.<CampusDO>lambdaQuery()
                    .eq(CampusDO::getTenantId, user.tenantId())
                    .eq(CampusDO::getEnabled, true))
            .stream()
            .map(CampusResp::from)
            .toList(),
        departments
            .selectList(
                Wrappers.<DepartmentDO>lambdaQuery()
                    .eq(DepartmentDO::getTenantId, user.tenantId())
                    .eq(DepartmentDO::getEnabled, true))
            .stream()
            .map(DepartmentResp::from)
            .toList(),
        doctors
            .selectList(
                Wrappers.<DoctorDO>lambdaQuery()
                    .eq(DoctorDO::getTenantId, user.tenantId())
                    .eq(DoctorDO::getEnabled, true))
            .stream()
            .map(DoctorResp::from)
            .toList());
  }

  public List<ScheduleDO> schedules(SessionUser user) {
    return schedules.selectList(
        Wrappers.<ScheduleDO>lambdaQuery()
            .eq(ScheduleDO::getTenantId, user.tenantId())
            .eq(ScheduleDO::getStatus, "OPEN")
            .ge(ScheduleDO::getEndAt, LocalDateTime.now(clock))
            .lt(ScheduleDO::getStartAt, LocalDateTime.now(clock).plusDays(30))
            .orderByAsc(ScheduleDO::getStartAt));
  }

  public List<SlotDO> slots(SessionUser user, long scheduleId) {
    return slots.selectList(
        Wrappers.<SlotDO>lambdaQuery()
            .eq(SlotDO::getTenantId, user.tenantId())
            .eq(SlotDO::getScheduleId, scheduleId)
            .orderByAsc(SlotDO::getStartAt));
  }

  public com.yiyue.model.resp.SchedulePageResp page(
      SessionUser u, com.yiyue.model.query.ScheduleQuery q) {
    var filter =
        Wrappers.<ScheduleDO>lambdaQuery()
            .eq(ScheduleDO::getTenantId, u.tenantId())
            .eq(ScheduleDO::getStatus, "OPEN")
            .ge(ScheduleDO::getEndAt, LocalDateTime.now(clock))
            .lt(ScheduleDO::getStartAt, LocalDateTime.now(clock).plusDays(30))
            .eq(q.campusId() != null, ScheduleDO::getCampusId, q.campusId())
            .eq(q.departmentId() != null, ScheduleDO::getDepartmentId, q.departmentId())
            .eq(q.doctorId() != null, ScheduleDO::getDoctorId, q.doctorId());
    if (q.date() != null)
      filter
          .ge(ScheduleDO::getStartAt, q.date().atStartOfDay())
          .lt(ScheduleDO::getStartAt, q.date().plusDays(1).atStartOfDay());
    if ("DOCTOR".equals(u.role())) {
      var owned = staffAccess.doctorIds(u);
      if (owned.isEmpty())
        return new com.yiyue.model.resp.SchedulePageResp(
            List.of(), 0, q.page() == null ? 1 : q.page(), q.size() == null ? 20 : q.size());
      filter.in(ScheduleDO::getDoctorId, owned);
    }
    filter.orderByAsc(ScheduleDO::getStartAt).orderByAsc(ScheduleDO::getId);
    var page =
        schedules.selectPage(
            new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(
                q.page() == null ? 1 : q.page(), q.size() == null ? 20 : q.size()),
            filter);
    return new com.yiyue.model.resp.SchedulePageResp(
        page.getRecords().stream().map(com.yiyue.model.resp.ScheduleResp::from).toList(),
        page.getTotal(),
        page.getCurrent(),
        page.getSize());
  }
}
