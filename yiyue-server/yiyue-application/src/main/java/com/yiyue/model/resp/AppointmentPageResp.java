package com.yiyue.model.resp;

import jakarta.validation.constraints.*;
import java.util.List;

public record AppointmentPageResp(
    List<AppointmentDetailResp> records, long total, long current, long size) {}
