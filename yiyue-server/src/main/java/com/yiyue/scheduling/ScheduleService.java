package com.yiyue.scheduling;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yiyue.auth.SessionUser;
import com.yiyue.common.BusinessException;
import com.yiyue.domain.*;
import com.yiyue.mapper.*;
import jakarta.validation.constraints.*;
import java.time.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ScheduleService {
  private final ScheduleMapper schedules;
  private final SlotMapper slots;
  private final CampusMapper campuses;
  private final DoctorMapper doctors;
  private final Clock clock;

  public record Create(
      @NotNull @Positive Long campusId,
      @NotNull @Positive Long doctorId,
      @NotNull LocalDateTime startAt,
      @NotNull LocalDateTime endAt,
      @Min(5) @Max(120) int slotMinutes,
      @Min(1) @Max(100) int capacity) {}

  @Transactional
  public Schedule create(SessionUser user, Create input) {
    if (input.endAt().isAfter(input.startAt().plusHours(12))
        || !input.endAt().isAfter(input.startAt())
        || !input.startAt().isAfter(LocalDateTime.now(clock))
        || !input.startAt().toLocalDate().equals(input.endAt().toLocalDate())
        || Duration.between(input.startAt(), input.endAt()).toSeconds()
                % (input.slotMinutes() * 60L)
            != 0) throw BusinessException.conflict("请选择未来同一天内、最长12小时且可按时段长度整分的排班");
    Campus campus =
        campuses.selectOne(
            Wrappers.<Campus>lambdaQuery()
                .eq(Campus::getTenantId, user.tenantId())
                .eq(Campus::getId, input.campusId()));
    Doctor doctor =
        doctors.selectOne(
            Wrappers.<Doctor>lambdaQuery()
                .eq(Doctor::getTenantId, user.tenantId())
                .eq(Doctor::getId, input.doctorId()));
    if (campus == null || doctor == null) throw BusinessException.missing();
    // 对同一医生串行校验排班，防止并发创建交叠时段（固定 XML 行锁）。
    doctors.lock(user.tenantId(), doctor.getId());
    boolean overlap =
        schedules.exists(
            Wrappers.<Schedule>lambdaQuery()
                .eq(Schedule::getTenantId, user.tenantId())
                .eq(Schedule::getDoctorId, doctor.getId())
                .lt(Schedule::getStartAt, input.endAt())
                .gt(Schedule::getEndAt, input.startAt()));
    if (overlap) throw BusinessException.conflict("该医生已有重叠排班，包括其他院区");
    Schedule schedule = new Schedule();
    schedule.setTenantId(user.tenantId());
    schedule.setCampusId(campus.getId());
    schedule.setDoctorId(doctor.getId());
    schedule.setStartAt(input.startAt());
    schedule.setEndAt(input.endAt());
    schedules.insert(schedule);
    // 排班和时段一起提交，避免出现有排班却没有号源的半成品数据。
    for (LocalDateTime start = input.startAt();
        start.isBefore(input.endAt());
        start = start.plusMinutes(input.slotMinutes())) {
      Slot slot = new Slot();
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
