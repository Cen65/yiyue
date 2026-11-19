package com.yiyue.model.req;

import jakarta.validation.constraints.*;

public record ResourceSaveReq(
    @NotNull @Positive Long campusId,
    @NotBlank @Pattern(regexp = "ROOM|EQUIPMENT") String kind,
    @NotBlank @Size(max = 80) String name,
    @NotNull Boolean enabled) {}
