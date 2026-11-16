package com.yiyue.model.req;

import jakarta.validation.constraints.*;

public record BookingReq(
    @NotNull @Positive Long slotId,
    @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9_-]+") String requestKey,
    Long patientId) {}
