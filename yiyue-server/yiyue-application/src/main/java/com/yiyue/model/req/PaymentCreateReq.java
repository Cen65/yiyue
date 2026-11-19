package com.yiyue.model.req;

import jakarta.validation.constraints.*;

public record PaymentCreateReq(
    @NotNull @Positive Long slotId,
    @Positive Long patientId,
    @NotBlank @Size(max = 80) String requestKey) {}
