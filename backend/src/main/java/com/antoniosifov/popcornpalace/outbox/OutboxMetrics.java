package com.antoniosifov.popcornpalace.outbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * The outbox as seen from outside: how many events are waiting and how old
 * the oldest one is. Both stay near zero while all is well. A growing age
 * means the relay cannot deliver (Kafka is down), and nobody would otherwise
 * notice, because the API keeps accepting bookings as designed.
 */
@Component
public class OutboxMetrics
{
    /** The backlog is read from the database at most this often, however many scrapes and health checks ask. */
    private static final Duration CACHE = Duration.ofSeconds(1);

    /** Events not yet sent, and the age of the oldest of them (zero when there are none). */
    public record Backlog(long pending, Duration oldestAge)
    {
    }

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final Counter published;
    private final Counter failures;

    private Backlog backlog = new Backlog(0, Duration.ZERO);
    private Instant backlogReadAt = Instant.MIN;

    public OutboxMetrics(JdbcTemplate jdbc, Clock clock, MeterRegistry registry)
    {
        this.jdbc = jdbc;
        this.clock = clock;
        Gauge.builder("popcorn.outbox.pending.events", this, metrics -> metrics.backlog().pending())
                .description("Events waiting in the outbox to be sent to Kafka")
                .register(registry);
        Gauge.builder("popcorn.outbox.oldest.event.age", this, metrics -> metrics.backlog().oldestAge().toMillis() / 1000.0)
                .description("Age of the oldest event waiting in the outbox")
                .baseUnit("seconds")
                .register(registry);
        published = Counter.builder("popcorn.outbox.published.events")
                .description("Events the relay delivered to Kafka")
                .register(registry);
        failures = Counter.builder("popcorn.outbox.publish.failures")
                .description("Batches Kafka did not acknowledge; they are sent again later")
                .register(registry);
    }

    public synchronized Backlog backlog()
    {
        Instant now = clock.instant();
        if (backlogReadAt.plus(CACHE).isAfter(now)) {
            return backlog;
        }
        try {
            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT count(*) AS pending, min(created_at) AS oldest FROM outbox_events WHERE published_at IS NULL");
            long pending = ((Number) row.get("pending")).longValue();
            Timestamp oldest = (Timestamp) row.get("oldest");
            backlog = new Backlog(pending, oldest == null ? Duration.ZERO : Duration.between(oldest.toInstant(), now));
            backlogReadAt = now;
        }
        catch (DataAccessException exception) {
            // The database is the problem, and its own health indicator says so; keep the last reading.
        }
        return backlog;
    }

    void published(int count)
    {
        published.increment(count);
    }

    void publishFailed()
    {
        failures.increment();
    }
}
