package com.yiyue.model.resp;

import com.yiyue.model.entity.AppointmentDO;
import java.time.LocalDateTime;

public record AppointmentResp(
    Long id,
    Long tenantId,
    Long userId,
    Long slotId,
    String requestKey,
    String status,
    LocalDateTime createdAt,
    Long patientId) {
  public static AppointmentResp from(AppointmentDO data) {
    return new AppointmentResp(
        data.getId(),
        data.getTenantId(),
        data.getUserId(),
        data.getSlotId(),
        data.getRequestKey(),
        data.getStatus(),
        data.getCreatedAt(),
        data.getPatientId());
  }
}
