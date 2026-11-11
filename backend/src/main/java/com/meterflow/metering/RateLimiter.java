package com.meterflow.metering;

import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RateLimiter {
  private final StringRedisTemplate redis;

  public RateLimiter(StringRedisTemplate redis) {
    this.redis = redis;
  }

  private static final DefaultRedisScript<Long> SCRIPT =
      new DefaultRedisScript<>(
          "local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],60) end; return n",
          Long.class);

  public void require(String key, int limit) {
    Long count;
    try {
      count = redis.execute(SCRIPT, List.of("meterflow:limit:" + key));
    } catch (org.springframework.dao.DataAccessException e) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "Rate limiter unavailable; retry later");
    }
    if (count == null || count > limit)
      throw new ResponseStatusException(
          HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded; retry in 60 seconds");
  }
}
