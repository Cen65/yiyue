package com.yiyue.triage;

import com.yiyue.auth.SessionUser;
import com.yiyue.common.BusinessException;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TriageRateLimiter {
  private final StringRedisTemplate redis;
  private final Clock clock;
  private static final DefaultRedisScript<Long> SCRIPT =
      new DefaultRedisScript<>(
          "local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],120) end; return n",
          Long.class);

  public void acquire(SessionUser user) {
    Long count;
    try {
      // 原子递增和过期，按机构与账号隔离；不使用症状文本作为缓存键。
      String key =
          "yiyue:triage:limit:"
              + user.tenantId()
              + ":"
              + user.id()
              + ":"
              + (clock.instant().getEpochSecond() / 60);
      count = redis.execute(SCRIPT, List.of(key));
    } catch (Exception ex) {
      throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "导诊服务暂时不可用，请稍后重试");
    }
    if (count == null) throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "导诊服务暂时不可用");
    if (count > 8) throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "咨询较频繁，请一分钟后再试");
  }
}
