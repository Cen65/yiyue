package com.yiyue.model.req;

import jakarta.validation.constraints.*;

public record TenantUpdateReq(@NotBlank @Size(max = 100) String name, @NotNull Boolean enabled) {}
