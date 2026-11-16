package com.yiyue.config;

import java.time.*;
import org.springframework.context.annotation.*;

@Configuration
public class ApplicationConfig {
  @Bean
  Clock clock() {
    return Clock.system(ZoneId.of("Asia/Shanghai"));
  }
}
