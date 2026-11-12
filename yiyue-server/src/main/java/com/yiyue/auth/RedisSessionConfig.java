package com.yiyue.auth;

import org.springframework.context.annotation.*;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

@Configuration
@Profile("!test")
@EnableRedisHttpSession(redisNamespace = "yiyue:session", maxInactiveIntervalInSeconds = 1800)
public class RedisSessionConfig {}
