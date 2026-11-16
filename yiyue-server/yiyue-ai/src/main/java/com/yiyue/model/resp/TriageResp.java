package com.yiyue.model.resp;

import jakarta.validation.constraints.*;
import java.util.List;

public record TriageResp(
    String urgency,
    List<DepartmentRecommendationResp> departments,
    List<String> questions,
    String message) {}
