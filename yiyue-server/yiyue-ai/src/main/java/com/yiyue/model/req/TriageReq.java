package com.yiyue.model.req;

import jakarta.validation.constraints.*;

public record TriageReq(
    @NotBlank @Size(min = 5, max = 1500) String symptoms,
    @AssertTrue(message = "请先确认向 DeepSeek 发送症状描述") boolean consent) {}
