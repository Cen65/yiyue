package com.yiyue.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yiyue.auth.SessionUser;
import com.yiyue.mapper.AppointmentMapper;
import com.yiyue.mapper.CampusMapper;
import com.yiyue.mapper.DoctorMapper;
import com.yiyue.mapper.ScheduleMapper;
import com.yiyue.mapper.SlotMapper;
import com.yiyue.model.entity.AppointmentDO;
import com.yiyue.model.entity.CampusDO;
import com.yiyue.model.entity.DoctorDO;
import com.yiyue.model.entity.ScheduleDO;
import com.yiyue.model.entity.SlotDO;
import com.yiyue.model.resp.AppointmentDetailResp;
import com.yiyue.model.resp.AppointmentPageResp;
import com.yiyue.service.AppointmentQueryService;
import com.yiyue.service.StaffAccessService;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AppointmentQueryServiceImpl implements AppointmentQueryService {
  private final AppointmentMapper appointments;
  private final SlotMapper slots;
  private final ScheduleMapper schedules;
  private final DoctorMapper doctors;
  private final CampusMapper campuses;
  private final com.yiyue.mapper.PatientProfileMapper patients;
  private final StaffAccessService staffAccess;

  public AppointmentPageResp list(SessionUser user, boolean admin, long current) {
    var query =
        Wrappers.<AppointmentDO>lambdaQuery()
            .eq(AppointmentDO::getTenantId, user.tenantId())
            .eq(!admin, AppointmentDO::getUserId, user.id())
            .orderByDesc(AppointmentDO::getId);
    if (admin && "DOCTOR".equals(user.role())) {
      var ids = staffAccess.doctorIds(user);
      if (ids.isEmpty()) return new AppointmentPageResp(List.of(), 0, current, 20);
      var owned =
          schedules.selectList(
              Wrappers.<ScheduleDO>lambdaQuery()
                  .eq(ScheduleDO::getTenantId, user.tenantId())
                  .in(ScheduleDO::getDoctorId, ids));
      if (owned.isEmpty()) return new AppointmentPageResp(List.of(), 0, current, 20);
      var ownedSlots =
          slots.selectList(
              Wrappers.<SlotDO>lambdaQuery()
                  .eq(SlotDO::getTenantId, user.tenantId())
                  .in(SlotDO::getScheduleId, owned.stream().map(ScheduleDO::getId).toList()));
      if (ownedSlots.isEmpty()) return new AppointmentPageResp(List.of(), 0, current, 20);
      query.in(AppointmentDO::getSlotId, ownedSlots.stream().map(SlotDO::getId).toList());
    }
    Page<AppointmentDO> page = appointments.selectPage(new Page<>(Math.max(1, current), 20), query);
    if (page.getRecords().isEmpty())
      return new AppointmentPageResp(List.of(), page.getTotal(), page.getCurrent(), 20);
    // 每页批量查询关联数据，在 Service 组装，避免 N+1 和复杂多表联查。
    var slotMap =
        index(
            slots.selectList(
                Wrappers.<SlotDO>lambdaQuery()
                    .eq(SlotDO::getTenantId, user.tenantId())
                    .in(
                        SlotDO::getId,
                        page.getRecords().stream().map(AppointmentDO::getSlotId).toList())),
            SlotDO::getId);
    var scheduleMap =
        index(
            schedules.selectList(
                Wrappers.<ScheduleDO>lambdaQuery()
                    .eq(ScheduleDO::getTenantId, user.tenantId())
                    .in(
                        ScheduleDO::getId,
                        slotMap.values().stream().map(SlotDO::getScheduleId).toList())),
            ScheduleDO::getId);
    var doctorMap =
        index(
            doctors.selectList(
                Wrappers.<DoctorDO>lambdaQuery()
                    .eq(DoctorDO::getTenantId, user.tenantId())
                    .in(
                        DoctorDO::getId,
                        scheduleMap.values().stream().map(ScheduleDO::getDoctorId).toList())),
            DoctorDO::getId);
    var campusMap =
        index(
            campuses.selectList(
                Wrappers.<CampusDO>lambdaQuery()
                    .eq(CampusDO::getTenantId, user.tenantId())
                    .in(
                        CampusDO::getId,
                        scheduleMap.values().stream().map(ScheduleDO::getCampusId).toList())),
            CampusDO::getId);
    var patientIds =
        page.getRecords().stream()
            .map(AppointmentDO::getPatientId)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();
    var patientMap =
        patientIds.isEmpty()
            ? java.util.Map.<Long, com.yiyue.model.entity.PatientProfileDO>of()
            : index(
                patients.selectList(
                    Wrappers.<com.yiyue.model.entity.PatientProfileDO>lambdaQuery()
                        .eq(com.yiyue.model.entity.PatientProfileDO::getTenantId, user.tenantId())
                        .in(com.yiyue.model.entity.PatientProfileDO::getId, patientIds)),
                com.yiyue.model.entity.PatientProfileDO::getId);
    var views =
        page.getRecords().stream()
            .map(
                a -> {
                  SlotDO slot = slotMap.get(a.getSlotId());
                  ScheduleDO schedule = scheduleMap.get(slot.getScheduleId());
                  return new AppointmentDetailResp(
                      a.getId(),
                      a.getUserId(),
                      a.getSlotId(),
                      a.getStatus(),
                      a.getCreatedAt(),
                      slot.getStartAt(),
                      slot.getEndAt(),
                      doctorMap.get(schedule.getDoctorId()).getName(),
                      campusMap.get(schedule.getCampusId()).getName(),
                      a.getPatientId(),
                      java.util.Optional.ofNullable(patientMap.get(a.getPatientId()))
                          .map(com.yiyue.model.entity.PatientProfileDO::getName)
                          .orElse("未记录"));
                })
            .toList();
    return new AppointmentPageResp(views, page.getTotal(), page.getCurrent(), 20);
  }

  private static <T> Map<Long, T> index(List<T> values, Function<T, Long> id) {
    return values.stream().collect(Collectors.toMap(id, Function.identity()));
  }
}
