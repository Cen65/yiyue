package com.yiyue.model.req;

import jakarta.validation.constraints.*;

public record PatientProfileReq(
    @NotBlank @Size(max = 80) String name,
    @NotBlank @Pattern(regexp = "SELF|CHILD|PARENT|SPOUSE|OTHER") String relationship,
    @AssertTrue(message = "请确认已取得就诊人授权") boolean consent) {}
