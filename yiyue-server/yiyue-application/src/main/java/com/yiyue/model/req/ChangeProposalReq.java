package com.yiyue.model.req;

import jakarta.validation.constraints.*;

public record ChangeProposalReq(@NotNull @Positive Long scheduleId) {}
