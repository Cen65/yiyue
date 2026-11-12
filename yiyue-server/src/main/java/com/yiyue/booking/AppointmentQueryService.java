package com.yiyue.booking;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yiyue.auth.SessionUser;
import com.yiyue.domain.*;
import com.yiyue.mapper.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AppointmentQueryService {
  private final AppointmentMapper appointments;
  private final SlotMapper slots;
  private final ScheduleMapper schedules;
  private final DoctorMapper doctors;
  private final CampusMapper campuses;

  public record View(
      Long id,
      Long userId,
      Long slotId,
      String status,
      LocalDateTime createdAt,
      LocalDateTime startAt,
      LocalDateTime endAt,
      String doctorName,
      String campusName) {}

  public record Result(List<View> records, long total, long current, long size) {}

  public Result list(SessionUser user, boolean admin, long current) {
    var query =
        Wrappers.<Appointment>lambdaQuery()
            .eq(Appointment::getTenantId, user.tenantId())
            .eq(!admin, Appointment::getUserId, user.id())
            .orderByDesc(Appointment::getId);
    Page<Appointment> page = appointments.selectPage(new Page<>(Math.max(1, current), 20), query);
    if (page.getRecords().isEmpty())
      return new Result(List.of(), page.getTotal(), page.getCurrent(), 20);
    // 每页批量查询关联数据，在 Service 组装，避免 N+1 和复杂多表联查。
    var slotMap =
        index(
            slots.selectList(
                Wrappers.<Slot>lambdaQuery()
                    .eq(Slot::getTenantId, user.tenantId())
                    .in(
                        Slot::getId,
                        page.getRecords().stream().map(Appointment::getSlotId).toList())),
            Slot::getId);
    var scheduleMap =
        index(
            schedules.selectList(
                Wrappers.<Schedule>lambdaQuery()
                    .eq(Schedule::getTenantId, user.tenantId())
                    .in(
                        Schedule::getId,
                        slotMap.values().stream().map(Slot::getScheduleId).toList())),
            Schedule::getId);
    var doctorMap =
        index(
            doctors.selectList(
                Wrappers.<Doctor>lambdaQuery()
                    .eq(Doctor::getTenantId, user.tenantId())
                    .in(
                        Doctor::getId,
                        scheduleMap.values().stream().map(Schedule::getDoctorId).toList())),
            Doctor::getId);
    var campusMap =
        index(
            campuses.selectList(
                Wrappers.<Campus>lambdaQuery()
                    .eq(Campus::getTenantId, user.tenantId())
                    .in(
                        Campus::getId,
                        scheduleMap.values().stream().map(Schedule::getCampusId).toList())),
            Campus::getId);
    var views =
        page.getRecords().stream()
            .map(
                a -> {
                  Slot slot = slotMap.get(a.getSlotId());
                  Schedule schedule = scheduleMap.get(slot.getScheduleId());
                  return new View(
                      a.getId(),
                      a.getUserId(),
                      a.getSlotId(),
                      a.getStatus(),
                      a.getCreatedAt(),
                      slot.getStartAt(),
                      slot.getEndAt(),
                      doctorMap.get(schedule.getDoctorId()).getName(),
                      campusMap.get(schedule.getCampusId()).getName());
                })
            .toList();
    return new Result(views, page.getTotal(), page.getCurrent(), 20);
  }

  private static <T> Map<Long, T> index(List<T> values, Function<T, Long> id) {
    return values.stream().collect(Collectors.toMap(id, Function.identity()));
  }
}
