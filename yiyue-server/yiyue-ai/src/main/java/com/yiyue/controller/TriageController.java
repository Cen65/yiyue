package com.yiyue.controller;

import com.yiyue.auth.SessionUser;
import com.yiyue.model.req.TriageReq;
import com.yiyue.model.resp.TriageResp;
import com.yiyue.service.TriageService;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/patient/triage")
@RequiredArgsConstructor
public class TriageController {
  private final TriageService service;

  @GetMapping("/status")
  Map<String, Object> status() {
    return Map.of("enabled", service.available(), "provider", "DeepSeek");
  }

  @PostMapping
  TriageResp recommend(
      @AuthenticationPrincipal SessionUser user, @Valid @RequestBody TriageReq request) {
    return service.recommend(user, request.symptoms());
  }
}
