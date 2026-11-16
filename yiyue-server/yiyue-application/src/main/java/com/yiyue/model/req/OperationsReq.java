package com.yiyue.model.req;

import jakarta.validation.constraints.*;
import java.util.List;

public record OperationsReq(
    @NotBlank @Size(max = 80) String name,
    @Size(max = 200) String address,
    @Size(max = 60) String title,
    @Size(max = 200) String specialty,
    Long departmentId,
    @Size(max = 50) List<@NotNull @Positive Long> departmentIds,
    @Size(max = 50) List<@NotNull @Positive Long> campusIds,
    @NotNull Boolean enabled) {}
