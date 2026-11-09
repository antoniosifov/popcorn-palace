package com.antoniosifov.popcornpalace.seatmap;

import com.antoniosifov.popcornpalace.hall.SeatStatusChanged;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.json.JsonMapper;

/**
 * Carries seat changes from the code that made them to every browser, across
 * every instance of the application.
 *
 * <p>A change is first published inside the application (Spring's event bus).
 * This relay waits for the surrounding database transaction to commit, so a
 * browser never hears about a sale that was rolled back, then publishes the
 * change on a Redis channel. Every instance subscribes to that channel (see
 * {@link SeatMapStreamConfig}) and pushes the change to its own browsers.
 *
 * <p>Redis Pub/Sub rather than Kafka: these messages are fan-out to whoever is
 * looking right now. Nobody needs them later, so durability would be waste, and
 * a browser that missed one simply reloads the seat map when it reconnects.
 */
@Component
public class SeatChangeRelay
{
    private static final Logger log = LoggerFactory.getLogger(SeatChangeRelay.class);
    private static final String CHANNEL_PREFIX = "seatmap:";

    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper;

    public SeatChangeRelay(StringRedisTemplate redis, JsonMapper jsonMapper)
    {
        this.redis = redis;
        this.jsonMapper = jsonMapper;
    }

    static String channel(Long showtimeId)
    {
        return CHANNEL_PREFIX + showtimeId;
    }

    static String channelPattern()
    {
        return CHANNEL_PREFIX + "*";
    }

    /**
     * After the commit when there is a transaction (bookings, cancellations);
     * right away when there is none (holds live in Redis, not in the database).
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void relay(SeatStatusChanged change)
    {
        try {
            redis.convertAndSend(channel(change.showtimeId()), jsonMapper.writeValueAsString(change));
        }
        catch (DataAccessException exception) {
            // Browsers will be a little stale until they reload; the change itself is safe in the database or in Redis.
            log.warn("Could not publish seat change for showtime {}: {}", change.showtimeId(), exception.getMessage());
        }
    }
}
