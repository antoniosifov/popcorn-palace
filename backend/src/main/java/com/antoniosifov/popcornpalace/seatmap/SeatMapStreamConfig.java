package com.antoniosifov.popcornpalace.seatmap;

import com.antoniosifov.popcornpalace.hall.SeatStatusChanged;
import com.antoniosifov.popcornpalace.hold.RedisSeatHoldStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Where this instance hears about seat changes from: the Redis channel the
 * relays of all instances publish on, and Redis itself when the seat key of a
 * hold expires.
 */
@Configuration
class SeatMapStreamConfig
{
    private static final Logger log = LoggerFactory.getLogger(SeatMapStreamConfig.class);

    /** Redis announces an expired key on this channel when configured to (see below). */
    private static final String EXPIRED_KEYS = "__keyevent@*__:expired";

    @Bean
    RedisMessageListenerContainer seatMapListeners(RedisConnectionFactory connectionFactory,
                                                   SeatMapBroadcaster broadcaster, JsonMapper jsonMapper)
    {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        // Seat changes published by any instance of the application, this one included.
        container.addMessageListener((message, pattern) -> {
            SeatStatusChanged change = jsonMapper.readValue(message.getBody(), SeatStatusChanged.class);
            broadcaster.broadcast(change);
        }, new PatternTopic(SeatChangeRelay.channelPattern()));

        // A hold ran out: Redis deleted its seat keys, and nobody else is around
        // to say so. Every instance receives this directly, so each pushes it to
        // its own browsers without publishing it again.
        container.addMessageListener((message, pattern) -> {
            String key = new String(message.getBody(), StandardCharsets.UTF_8);
            RedisSeatHoldStore.parseSeatKey(key).ifPresent(seat ->
                    broadcaster.broadcast(SeatStatusChanged.available(seat.showtimeId(), List.of(seat.seatId()))));
        }, new PatternTopic(EXPIRED_KEYS));

        return container;
    }

    /**
     * Redis does not announce expired keys unless told to. Asked at startup, so
     * a plain Redis works out of the box; a managed Redis that forbids CONFIG
     * must have it set by its own means (a warning says so).
     */
    @Bean
    ApplicationRunner enableExpiredKeyNotifications(StringRedisTemplate redis)
    {
        return args -> {
            try {
                redis.execute((RedisCallback<Void>) connection -> {
                    connection.serverCommands().setConfig("notify-keyspace-events", "Ex");
                    return null;
                });
            }
            catch (RuntimeException exception) {
                log.warn("Could not enable Redis expired-key notifications (set notify-keyspace-events=Ex on the "
                        + "server); seats freed by expired holds will not appear live: {}", exception.getMessage());
            }
        };
    }
}
