package com.yiyue.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;

@Configuration
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(
    name = "yiyue.waitlist.worker-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class WaitlistRunner {
  private final com.yiyue.service.WaitlistService service;

  @Scheduled(fixedDelay = 5000)
  public void poll() {
    service.poll();
  }
}
