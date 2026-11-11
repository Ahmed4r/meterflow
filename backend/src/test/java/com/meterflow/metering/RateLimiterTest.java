package com.meterflow.metering;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.web.server.ResponseStatusException;

class RateLimiterTest {
  @Test
  void redisFailureFailsClosed() {
    var redis = mock(StringRedisTemplate.class);
    when(redis.execute(any(RedisScript.class), anyList()))
        .thenThrow(new DataAccessResourceFailureException("synthetic failure"));
    assertThatThrownBy(() -> new RateLimiter(redis).require("test", 600))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            e -> assertThat(e.getStatusCode().value()).isEqualTo(503));
  }

  @Test
  void requestsBeyondLimitAreRejected() {
    var redis = mock(StringRedisTemplate.class);
    when(redis.execute(any(RedisScript.class), anyList())).thenReturn(601L);
    assertThatThrownBy(() -> new RateLimiter(redis).require("test", 600))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            e -> assertThat(e.getStatusCode().value()).isEqualTo(429));
  }
}
