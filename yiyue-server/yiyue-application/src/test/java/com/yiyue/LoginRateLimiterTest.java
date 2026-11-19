package com.yiyue;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.yiyue.auth.LoginRateLimiter;
import com.yiyue.common.BusinessException;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class LoginRateLimiterTest {
  StringRedisTemplate redis;
  LoginRateLimiter limiter;

  @BeforeEach
  void setup() {
    redis = mock(StringRedisTemplate.class);
    limiter =
        new LoginRateLimiter(
            redis, Clock.fixed(Instant.parse("2026-11-19T00:00:00Z"), ZoneOffset.UTC));
    ReflectionTestUtils.setField(limiter, "enabled", true);
    ReflectionTestUtils.setField(limiter, "accountLimit", 8);
    ReflectionTestUtils.setField(limiter, "ipLimit", 60);
  }

  @Test
  void permitsWithinLimit() {
    when(redis.execute(
            any(org.springframework.data.redis.core.script.RedisScript.class), anyList()))
        .thenReturn(1L);
    limiter.acquire("127.0.0.1", "demo", "fake");
    verify(redis, times(2))
        .execute(any(org.springframework.data.redis.core.script.RedisScript.class), anyList());
  }

  @Test
  void rejectsAccountThreshold() {
    when(redis.execute(
            any(org.springframework.data.redis.core.script.RedisScript.class), anyList()))
        .thenReturn(1L, 9L);
    assertThatThrownBy(() -> limiter.acquire("127.0.0.1", "demo", "fake"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("较频繁");
  }

  @Test
  void redisFailureDoesNotBypass() {
    when(redis.execute(
            any(org.springframework.data.redis.core.script.RedisScript.class), anyList()))
        .thenThrow(new IllegalStateException());
    assertThatThrownBy(() -> limiter.acquire("127.0.0.1", "demo", "fake"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("不可用");
  }

  @Test
  void keysDoNotContainRawIdentity() {
    when(redis.execute(
            any(org.springframework.data.redis.core.script.RedisScript.class), anyList()))
        .thenReturn(1L);
    limiter.acquire("192.0.2.123", "virtual-tenant", "virtual-user");
    var captor = org.mockito.ArgumentCaptor.forClass(List.class);
    verify(redis, times(2))
        .execute(
            any(org.springframework.data.redis.core.script.RedisScript.class), captor.capture());
    for (var key : captor.getAllValues())
      assertThat(key.toString()).doesNotContain("192.0.2.123", "virtual-tenant", "virtual-user");
  }
}
