package com.yiyue.triage;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(TriageProperties.class)
public class TriageConfig {
  @Bean
  RestClient deepSeekRestClient(RestClient.Builder builder) {
    // 固定官方地址且禁止重定向，避免凭证经配置错误发送至其他站点。
    var http =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    var factory = new JdkClientHttpRequestFactory(http);
    factory.setReadTimeout(Duration.ofSeconds(25));
    return builder.clone().requestFactory(factory).baseUrl("https://api.deepseek.com").build();
  }
}
