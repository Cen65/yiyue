package com.yiyue.model.resp;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

public record AppointmentDetailResp(
    Long id,
    Long userId,
    Long slotId,
    String status,
    LocalDateTime createdAt,
    LocalDateTime startAt,
    LocalDateTime endAt,
    String doctorName,
    String campusName,
    Long patientId,
    String patientName) {}
