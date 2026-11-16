package com.yiyue.triage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties("yiyue.ai")
public class TriageProperties {
  private boolean enabled = false;
  private String apiKey = "";
  private String model = "deepseek-flash";

  public boolean isAvailable() {
    return enabled && apiKey != null && !apiKey.isBlank();
  }
}
