package com.yiyue.model.req;

import jakarta.validation.constraints.*;

public record ChangeAcceptReq(
    @NotNull @Positive Long slotId, @AssertTrue(message = "请确认新的医生和就诊时段") boolean consent) {}
