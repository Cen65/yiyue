package com.yiyue.model.resp;

import java.time.LocalDateTime;

public record ScheduleChangeResp(
    Long id,
    Long scheduleId,
    Long replacementScheduleId,
    String kind,
    String reason,
    String status,
    LocalDateTime createdAt,
    long total,
    long done,
    long failed,
    Long campusId,
    Long departmentId) {}
