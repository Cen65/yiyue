package com.yiyue.model.req;

import jakarta.validation.constraints.*;

public record ScheduleChangeReq(
    @NotBlank @Size(max = 200) String reason,
    @NotBlank @Pattern(regexp = "STOP|SUBSTITUTE") String kind,
    Long replacementDoctorId) {}
