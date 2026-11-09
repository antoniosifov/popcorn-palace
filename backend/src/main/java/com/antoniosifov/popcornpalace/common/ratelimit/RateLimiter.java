package com.antoniosifov.popcornpalace.common.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Counts requests per key in fixed time windows, in Redis so that every
 * instance of the application shares the same counters.
 */
@Component
public class RateLimiter
{
    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> COUNT =
            RedisScript.of(new ClassPathResource("redis/rate_limit.lua"), List.class);

    private final StringRedisTemplate redis;

    public RateLimiter(StringRedisTemplate redis)
    {
        this.redis = redis;
    }

    /**
     * Counts one request against the key.
     *
     * @throws RateLimitExceededException when more than {@code maxRequests} were made in the current window
     */
    public void check(String key, int maxRequests, Duration window)
    {
        List<?> result;
        try {
            result = redis.execute(COUNT, List.of("ratelimit:" + key), String.valueOf(window.toMillis()));
        }
        catch (DataAccessException exception) {
            // Fail open: if Redis is down, letting people log in matters more
            // than throttling them. The outage itself is visible in the logs.
            log.warn("Rate limiting is unavailable; allowing the request", exception);
            return;
        }

        long count = ((Number) result.get(0)).longValue();
        long millisUntilReset = ((Number) result.get(1)).longValue();
        if (count > maxRequests) {
            long retryAfterSeconds = Math.max(1, (millisUntilReset + 999) / 1000);
            throw new RateLimitExceededException(retryAfterSeconds);
        }
    }
}
