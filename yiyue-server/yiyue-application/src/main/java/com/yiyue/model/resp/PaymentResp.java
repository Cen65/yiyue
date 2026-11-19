package com.yiyue.model.resp;

import java.time.LocalDateTime;

public record PaymentResp(
    Long id,
    Long slotId,
    Long patientId,
    Integer amountCents,
    String status,
    LocalDateTime expiresAt,
    Long appointmentId,
    String channel,
    String doctorName,
    String campusName,
    String patientName,
    LocalDateTime startAt,
    LocalDateTime endAt) {}
