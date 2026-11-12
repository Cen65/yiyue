package com.yiyue.triage;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.yiyue.auth.SessionUser;
import com.yiyue.common.BusinessException;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;

class TriageRateLimiterTest {
  @Test
  void rejectsNinthRequestAndRedisFailure() {
    var redis = mock(StringRedisTemplate.class);
    var limiter = new TriageRateLimiter(redis, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    var user = new SessionUser(1, 2, "测试", "PATIENT");
    when(redis.execute(
            any(org.springframework.data.redis.core.script.RedisScript.class), anyList()))
        .thenReturn(8L, 9L);
    assertThatCode(() -> limiter.acquire(user)).doesNotThrowAnyException();
    assertThatThrownBy(() -> limiter.acquire(user))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    when(redis.execute(
            any(org.springframework.data.redis.core.script.RedisScript.class), anyList()))
        .thenThrow(new RuntimeException("redis unavailable"));
    assertThatThrownBy(() -> limiter.acquire(user))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
  }
}
