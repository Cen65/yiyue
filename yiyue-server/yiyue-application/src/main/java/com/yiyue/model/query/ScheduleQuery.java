package com.yiyue.model.query;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

public record ScheduleQuery(
    @Min(1) @Max(1000000) Integer page,
    @Min(1) @Max(100) Integer size,
    @Positive Long campusId,
    @Positive Long departmentId,
    @Positive Long doctorId,
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {}
