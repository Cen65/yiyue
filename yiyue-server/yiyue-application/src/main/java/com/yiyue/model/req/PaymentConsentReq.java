package com.yiyue.model.req;

import jakarta.validation.constraints.AssertTrue;

public record PaymentConsentReq(@AssertTrue(message = "请确认这是模拟支付，不会真实扣款") boolean consent) {}
