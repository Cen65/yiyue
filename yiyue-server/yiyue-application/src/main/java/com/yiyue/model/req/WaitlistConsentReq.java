package com.yiyue.model.req;

import jakarta.validation.constraints.*;

public record WaitlistConsentReq(@AssertTrue boolean consent) {}
