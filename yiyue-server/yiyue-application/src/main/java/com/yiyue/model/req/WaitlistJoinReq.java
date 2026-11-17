package com.yiyue.model.req;

import jakarta.validation.constraints.*;

public record WaitlistJoinReq(@NotNull @Positive Long slotId, @Positive Long patientId) {}
