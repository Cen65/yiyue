package com.yiyue.model.req;

import jakarta.validation.constraints.*;

public record TenantCreateReq(
    @NotBlank @Pattern(regexp = "[a-z0-9_-]{3,40}") String code,
    @NotBlank @Size(max = 100) String name,
    @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{3,50}") String adminUsername,
    @NotBlank @Size(min = 12, max = 100) String adminPassword) {}
