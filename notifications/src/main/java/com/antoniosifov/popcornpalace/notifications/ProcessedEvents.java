package com.antoniosifov.popcornpalace.notifications;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Remembers which events have already been handled. Kafka (and the outbox
 * that feeds it) delivers every event at least once, which means sometimes
 * twice; without this, a redelivery would send the customer a second email.
 *
 * <p>Each handled event id is a Redis key that expires after a while, so the
 * memory does not grow forever. Redeliveries arrive seconds or minutes after
 * the original, never days later.
 */
@Component
public class ProcessedEvents
{
    private final StringRedisTemplate redis;
    private final NotificationProperties properties;

    public ProcessedEvents(StringRedisTemplate redis, NotificationProperties properties)
    {
        this.redis = redis;
        this.properties = properties;
    }

    public boolean contains(UUID eventId)
    {
        return Boolean.TRUE.equals(redis.hasKey(key(eventId)));
    }

    public void add(UUID eventId)
    {
        redis.opsForValue().set(key(eventId), "1", properties.processedTtl());
    }

    private static String key(UUID eventId)
    {
        return "notifications:processed:" + eventId;
    }
}
