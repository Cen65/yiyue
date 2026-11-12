package com.yiyue.triage;

import com.yiyue.auth.SessionUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/patient/triage")
@RequiredArgsConstructor
public class TriageController {
  private final TriageService service;

  public record Request(
      @NotBlank @Size(min = 5, max = 1500) String symptoms,
      @AssertTrue(message = "请先确认向 DeepSeek 发送症状描述") boolean consent) {}

  @GetMapping("/status")
  Map<String, Object> status() {
    return Map.of("enabled", service.available(), "provider", "DeepSeek");
  }

  @PostMapping
  TriageService.Result recommend(
      @AuthenticationPrincipal SessionUser user, @Valid @RequestBody Request request) {
    return service.recommend(user, request.symptoms());
  }
}
