package com.yiyue.model.req;

import jakarta.validation.constraints.*;

public record StaffCreateReq(
    @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{3,50}") String username,
    @NotBlank @Size(min = 12, max = 100) String password,
    @NotBlank @Size(max = 80) String name,
    @NotBlank @Pattern(regexp = "DOCTOR|RECEPTION") String role,
    Long doctorId) {}
