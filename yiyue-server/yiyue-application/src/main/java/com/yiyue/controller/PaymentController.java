package com.yiyue.controller;

import com.yiyue.auth.SessionUser;
import com.yiyue.model.req.*;
import com.yiyue.model.resp.*;
import com.yiyue.service.PaymentService;
import jakarta.validation.Valid;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "yiyue.payment.mock-enabled",
    havingValue = "true",
    matchIfMissing = true)
@RestController
@RequiredArgsConstructor
public class PaymentController {
  private final PaymentService service;

  @PostMapping("/api/patient/payment-orders")
  public PaymentResp create(
      @AuthenticationPrincipal SessionUser u, @Valid @RequestBody PaymentCreateReq r) {
    return service.create(u, r);
  }

  @GetMapping("/api/patient/payment-orders")
  public List<PaymentResp> mine(@AuthenticationPrincipal SessionUser u) {
    return service.mine(u);
  }

  @PostMapping("/api/patient/payment-orders/{id}/mock-pay")
  public PaymentResp pay(
      @AuthenticationPrincipal SessionUser u,
      @PathVariable long id,
      @Valid @RequestBody PaymentConsentReq r) {
    return service.mockPay(u, id);
  }

  @PostMapping("/api/patient/payment-orders/{id}/cancel")
  public void cancel(@AuthenticationPrincipal SessionUser u, @PathVariable long id) {
    service.cancel(u, id);
  }

  @GetMapping("/api/admin/payment-reconciliation")
  public List<Map<String, Object>> reconcile(@AuthenticationPrincipal SessionUser u) {
    return service.reconcile(u);
  }
}
