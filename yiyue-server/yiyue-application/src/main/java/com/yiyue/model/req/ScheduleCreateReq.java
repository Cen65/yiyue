package com.yiyue.model.req;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

public record ScheduleCreateReq(
    @NotNull @Positive Long campusId,
    @NotNull @Positive Long doctorId,
    @NotNull LocalDateTime startAt,
    @NotNull LocalDateTime endAt,
    @Min(5) @Max(120) int slotMinutes,
    @Min(1) @Max(100) int capacity,
    Long departmentId) {
  public ScheduleCreateReq(
      Long campusId,
      Long doctorId,
      LocalDateTime startAt,
      LocalDateTime endAt,
      int slotMinutes,
      int capacity) {
    this(campusId, doctorId, startAt, endAt, slotMinutes, capacity, null);
  }
}
