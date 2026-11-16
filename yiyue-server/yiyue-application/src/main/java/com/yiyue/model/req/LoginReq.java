package com.yiyue.model.req;

import jakarta.validation.constraints.*;

public record LoginReq(
    @NotBlank @Size(max = 40) String tenantCode,
    @NotBlank @Size(max = 50) String username,
    @NotBlank @Size(max = 100) String password) {}
