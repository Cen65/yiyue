package com.yiyue.scheduling;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yiyue.auth.SessionUser;
import com.yiyue.domain.*;
import com.yiyue.mapper.*;
import jakarta.validation.Valid;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class CatalogController {
  private final CampusMapper campuses;
  private final DepartmentMapper departments;
  private final DoctorMapper doctors;
  private final ScheduleMapper schedules;
  private final SlotMapper slots;
  private final ScheduleService service;
  private final Clock clock;

  @GetMapping("/api/catalog")
  Map<String, Object> catalog(@AuthenticationPrincipal SessionUser user) {
    return Map.of(
        "campuses",
        campuses.selectList(
            Wrappers.<Campus>lambdaQuery().eq(Campus::getTenantId, user.tenantId())),
        "departments",
        departments.selectList(
            Wrappers.<Department>lambdaQuery().eq(Department::getTenantId, user.tenantId())),
        "doctors",
        doctors.selectList(
            Wrappers.<Doctor>lambdaQuery().eq(Doctor::getTenantId, user.tenantId())));
  }

  @GetMapping("/api/schedules")
  List<Schedule> schedules(@AuthenticationPrincipal SessionUser user) {
    return schedules.selectList(
        Wrappers.<Schedule>lambdaQuery()
            .eq(Schedule::getTenantId, user.tenantId())
            .ge(Schedule::getEndAt, LocalDateTime.now(clock))
            .lt(Schedule::getStartAt, LocalDateTime.now(clock).plusDays(30))
            .orderByAsc(Schedule::getStartAt));
  }

  @GetMapping("/api/slots")
  List<Slot> slots(@AuthenticationPrincipal SessionUser user, @RequestParam long scheduleId) {
    return slots.selectList(
        Wrappers.<Slot>lambdaQuery()
            .eq(Slot::getTenantId, user.tenantId())
            .eq(Slot::getScheduleId, scheduleId)
            .orderByAsc(Slot::getStartAt));
  }

  @PostMapping("/api/admin/schedules")
  Schedule create(
      @AuthenticationPrincipal SessionUser user, @Valid @RequestBody ScheduleService.Create input) {
    return service.create(user, input);
  }
}
