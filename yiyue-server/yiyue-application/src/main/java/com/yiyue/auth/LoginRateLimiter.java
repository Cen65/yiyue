package com.yiyue.auth;

import com.yiyue.common.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Clock;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LoginRateLimiter {
  private final StringRedisTemplate redis;
  private final Clock clock;

  @Value("${yiyue.login.limit-enabled:true}")
  private boolean enabled;

  @Value("${yiyue.login.account-limit:60}")
  private int accountLimit;

  @Value("${yiyue.login.ip-limit:300}")
  private int ipLimit;

  private static final DefaultRedisScript<Long> SCRIPT =
      new DefaultRedisScript<>(
          "local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],120) end; return n",
          Long.class);

  public void acquire(String address, String tenant, String username) {
    if (!enabled) return;
    // 只使用实际连接地址，不信任客户端伪造的Forwarded头；缓存键不保存原始账号/IP。
    enforce("ip", address, ipLimit);
    enforce("account", address + "\n" + tenant + "\n" + username, accountLimit);
  }

  private void enforce(String scope, String identity, int limit) {
    Long count;
    try {
      var digest =
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(identity.getBytes(StandardCharsets.UTF_8)));
      count =
          redis.execute(
              SCRIPT,
              List.of(
                  "yiyue:login:"
                      + scope
                      + ":"
                      + digest
                      + ":"
                      + clock.instant().getEpochSecond() / 60));
    } catch (Exception ex) {
      throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "登录服务暂时不可用");
    }
    if (count == null) throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "登录服务暂时不可用");
    if (count > limit) throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "登录较频繁，请一分钟后再试");
  }
}
