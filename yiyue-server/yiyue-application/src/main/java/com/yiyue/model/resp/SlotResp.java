package com.yiyue.model.resp;

import com.yiyue.model.entity.SlotDO;
import java.time.LocalDateTime;

public record SlotResp(
    Long id,
    Long tenantId,
    Long scheduleId,
    LocalDateTime startAt,
    LocalDateTime endAt,
    Integer capacity,
    Integer reserved) {
  public static SlotResp from(SlotDO data) {
    return new SlotResp(
        data.getId(),
        data.getTenantId(),
        data.getScheduleId(),
        data.getStartAt(),
        data.getEndAt(),
        data.getCapacity(),
        data.getReserved());
  }
}
