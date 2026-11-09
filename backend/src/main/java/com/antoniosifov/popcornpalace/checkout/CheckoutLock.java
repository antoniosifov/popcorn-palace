package com.antoniosifov.popcornpalace.checkout;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lets only one request at a time pay for a given hold. Without it, two
 * requests for the same hold (a double click, or a retry racing the original)
 * could both reach the payment provider and charge the customer twice; one of
 * the charges would then have to be refunded. The lock stops the second request
 * before any money moves.
 *
 * <p>It is a Redis key set with NX ("only if absent") and a time to live, so a
 * crashed request cannot keep a hold locked forever.
 */
@Component
class CheckoutLock
{
    private static final RedisScript<Long> RELEASE =
            RedisScript.of(new ClassPathResource("redis/compare_and_delete.lua"), Long.class);

    /** Longer than a payment can take; only matters if the application dies while holding the lock. */
    private static final Duration TIME_TO_LIVE = Duration.ofSeconds(60);

    private final StringRedisTemplate redis;

    CheckoutLock(StringRedisTemplate redis)
    {
        this.redis = redis;
    }

    /** A held lock; pass it back to {@link #release}. */
    record Handle(String key, String owner)
    {
    }

    /** @return the lock, or empty if another request is paying for this hold right now */
    Optional<Handle> tryAcquire(long showtimeId, UUID holdId)
    {
        // Same hash tag as the hold's own keys, so they share a slot on Redis Cluster.
        String key = "hold:{" + showtimeId + "}:paying:" + holdId;
        String owner = UUID.randomUUID().toString();
        Boolean acquired = redis.opsForValue().setIfAbsent(key, owner, TIME_TO_LIVE);
        return Boolean.TRUE.equals(acquired) ? Optional.of(new Handle(key, owner)) : Optional.empty();
    }

    void release(Handle handle)
    {
        redis.execute(RELEASE, List.of(handle.key()), handle.owner());
    }
}
