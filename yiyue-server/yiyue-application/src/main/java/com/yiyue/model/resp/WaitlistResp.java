package com.yiyue.model.resp;

import java.time.LocalDateTime;

public record WaitlistResp(
    Long id,
    Long slotId,
    Long patientId,
    String status,
    LocalDateTime expiresAt,
    Long appointmentId,
    LocalDateTime startAt,
    LocalDateTime endAt,
    String doctorName,
    String campusName,
    String patientName) {}
