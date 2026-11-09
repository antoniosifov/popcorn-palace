package com.antoniosifov.popcornpalace.outbox;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Where events are put to be sent to Kafka later. See the comment in
 * migration V6 for why events are not sent to Kafka directly.
 */
@Component
public class Outbox
{
    private final JdbcTemplate jdbc;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public Outbox(JdbcTemplate jdbc, JsonMapper jsonMapper, Clock clock)
    {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
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
        jdbc.update("""
                        INSERT INTO outbox_events (event_id, topic, message_key, event_type, payload, created_at)
                        VALUES (?, ?, ?, ?, ?::json, ?)""",
                eventId, topic, key, eventType, jsonMapper.writeValueAsString(payload),
                Timestamp.from(clock.instant().truncatedTo(ChronoUnit.MILLIS)));
    }
}
