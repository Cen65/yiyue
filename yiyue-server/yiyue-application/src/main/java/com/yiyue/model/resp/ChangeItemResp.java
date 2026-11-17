package com.yiyue.model.resp;

public record ChangeItemResp(
    Long id,
    Long changeId,
    Long appointmentId,
    Long targetScheduleId,
    Long newAppointmentId,
    String status,
    String decision,
    int attempts,
    String lastError,
    String reason) {}
