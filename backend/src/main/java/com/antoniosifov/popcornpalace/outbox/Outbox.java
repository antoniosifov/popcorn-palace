package com.antoniosifov.popcornpalace.outbox;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Where events are put to be sent to Kafka later. See the comment in
 * migration V6 for why events are not sent to Kafka directly.
 */
@Component
public class Outbox
{
    /** The W3C header that names a trace and the span to continue from. */
    public static final String TRACE_PARENT = "traceparent";

    private final JdbcTemplate jdbc;
    private final JsonMapper jsonMapper;
    private final Tracer tracer;
    private final Propagator propagator;
    private final Clock clock;

    public Outbox(JdbcTemplate jdbc, JsonMapper jsonMapper, Tracer tracer, Propagator propagator, Clock clock)
    {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
        this.tracer = tracer;
        this.propagator = propagator;
        this.clock = clock;
    }

    /**
     * Stores an event as part of the caller's transaction: it becomes visible
     * to the relay only if, and exactly when, that transaction commits.
     *
     * <p>MANDATORY makes misuse fail loudly: calling this outside a transaction
     * would save the event on its own, which is precisely the bug the outbox
     * exists to prevent.
     *
     * @param key messages with the same key are delivered in order
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(String topic, String key, String eventType, UUID eventId, Object payload)
    {
        insert(topic, key, eventType, eventId, payload, false);
    }

    /**
     * The same, for an event that carries a secret, such as a link that lets its
     * holder into an account. Published events are normally kept for a while for
     * troubleshooting; this one is deleted as soon as Kafka has acknowledged it,
     * so the secret does not sit in the database longer than it takes to send.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void appendConfidential(String topic, String key, String eventType, UUID eventId, Object payload)
    {
        insert(topic, key, eventType, eventId, payload, true);
    }

    private void insert(String topic, String key, String eventType, UUID eventId, Object payload, boolean confidential)
    {
        jdbc.update("""
                        INSERT INTO outbox_events (event_id, topic, message_key, event_type, payload, created_at, trace_parent, confidential)
                        VALUES (?, ?, ?, ?, ?::json, ?, ?, ?)""",
                eventId, topic, key, eventType, jsonMapper.writeValueAsString(payload),
                Timestamp.from(clock.instant().truncatedTo(ChronoUnit.MILLIS)), traceParent(), confidential);
    }

    /**
     * The trace of the request that produced the event, as a {@code traceparent}
     * header. The relay continues it when it sends the message, which may happen
     * on another thread or another instance. Null when there is no trace.
     */
    private String traceParent()
    {
        Span span = tracer.currentSpan();
        if (span == null) {
            return null;
        }
        Map<String, String> headers = new HashMap<>();
        propagator.inject(span.context(), headers, Map::put);
        return headers.get(TRACE_PARENT);
    }
}
