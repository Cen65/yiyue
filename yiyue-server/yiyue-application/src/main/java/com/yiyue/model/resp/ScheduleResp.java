package com.yiyue.model.resp;

import com.yiyue.model.entity.ScheduleDO;
import java.time.LocalDateTime;

public record ScheduleResp(
    Long id,
    Long tenantId,
    Long campusId,
    Long doctorId,
    LocalDateTime startAt,
    LocalDateTime endAt,
    Long departmentId,
    String status,
    String stopReason) {
  public static ScheduleResp from(ScheduleDO data) {
    return new ScheduleResp(
        data.getId(),
        data.getTenantId(),
        data.getCampusId(),
        data.getDoctorId(),
        data.getStartAt(),
        data.getEndAt(),
        data.getDepartmentId(),
        data.getStatus(),
        data.getStopReason());
  }
}
