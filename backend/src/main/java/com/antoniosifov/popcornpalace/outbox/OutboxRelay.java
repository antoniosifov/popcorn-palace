package com.antoniosifov.popcornpalace.outbox;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.transport.ReceiverContext;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Sends events from the outbox table to Kafka.
 *
 * <p>One run takes a batch of unpublished rows, sends them, waits for Kafka
 * to acknowledge all of them and only then marks them published, all inside
 * one database transaction. If anything fails the transaction rolls back and
 * the rows are simply picked up again, so an event can be sent twice but is
 * never lost (at-least-once delivery).
 *
 * <p>Only one relay publishes at a time, even when several instances of the
 * application are running: each run first takes a PostgreSQL advisory lock
 * and does nothing if another instance holds it. Letting instances publish
 * different rows in parallel would be faster, but then a later event could
 * overtake an earlier one, and consumers must see a booking's "confirmed"
 * before its "cancelled".
 */
@Component
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxRelay
{
    /** Kafka header carrying the event type, so consumers can route without parsing the body. */
    public static final String EVENT_TYPE_HEADER = "eventType";
    public static final String EVENT_ID_HEADER = "eventId";

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final Duration MAX_BACKOFF = Duration.ofSeconds(30);

    /** Arbitrary number that identifies "the outbox relay" among PostgreSQL advisory locks. */
    private static final long RELAY_LOCK = 7_021_001L;

    private record PendingEvent(long id, String eventId, String topic, String key, String eventType, String payload,
                                String traceParent)
    {
    }

    private final JdbcTemplate jdbc;
    private final KafkaOperations<String, String> kafka;
    private final TransactionTemplate transaction;
    private final OutboxProperties properties;
    private final ObservationRegistry observations;
    private final OutboxMetrics metrics;
    private final Clock clock;

    /** After a failure the relay waits before trying again, a little longer each time. */
    private Duration backoff = Duration.ZERO;
    private Instant retryNotBefore = Instant.MIN;

    public OutboxRelay(JdbcTemplate jdbc, KafkaOperations<String, String> kafka,
                       PlatformTransactionManager transactionManager, OutboxProperties properties,
                       ObservationRegistry observations, OutboxMetrics metrics, Clock clock)
    {
        this.jdbc = jdbc;
        this.kafka = kafka;
        this.transaction = new TransactionTemplate(transactionManager);
        this.properties = properties;
        this.observations = observations;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.outbox.relay-interval}")
    void runOnSchedule()
    {
        if (clock.instant().isBefore(retryNotBefore)) {
            return;
        }
        try {
            // A full batch means there may be more waiting: keep going, so a backlog drains quickly.
            int published;
            do {
                published = publishPending();
            }
            while (published == properties.batchSize());
            backoff = Duration.ZERO;
        }
        catch (RuntimeException exception) {
            backoff = backoff.isZero() ? Duration.ofSeconds(1) : min(backoff.multipliedBy(2), MAX_BACKOFF);
            retryNotBefore = clock.instant().plus(backoff);
            log.warn("Could not publish outbox events (will retry in {} s): {}", backoff.toSeconds(), exception.toString());
        }
    }

    /**
     * Publishes one batch, oldest events first.
     *
     * @return how many events were published; 0 if there were none or another relay is publishing right now
     * @throws OutboxPublishException if Kafka did not acknowledge the batch; nothing is marked published
     */
    public int publishPending()
    {
        Integer published = transaction.execute(status -> {
            // Held until this transaction ends; a second relay gets false here and steps aside.
            Boolean lockTaken = jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(?)", Boolean.class, RELAY_LOCK);
            if (!Boolean.TRUE.equals(lockTaken)) {
                return 0;
            }

            List<PendingEvent> batch = jdbc.query("""
                            SELECT id, event_id, topic, message_key, event_type, payload::text AS payload, trace_parent
                            FROM outbox_events
                            WHERE published_at IS NULL
                            ORDER BY id
                            LIMIT ?""",
                    (row, rowNumber) -> new PendingEvent(row.getLong("id"), row.getString("event_id"),
                            row.getString("topic"), row.getString("message_key"),
                            row.getString("event_type"), row.getString("payload"), row.getString("trace_parent")),
                    properties.batchSize());
            if (batch.isEmpty()) {
                return 0;
            }

            send(batch);

            Timestamp now = Timestamp.from(clock.instant());
            jdbc.batchUpdate("UPDATE outbox_events SET published_at = ? WHERE id = ?", batch, batch.size(),
                    (statement, event) -> {
                        statement.setTimestamp(1, now);
                        statement.setLong(2, event.id());
                    });
            // What carried a secret is not kept once it has been delivered (see Outbox.appendConfidential).
            jdbc.update("DELETE FROM outbox_events WHERE confidential AND published_at IS NOT NULL");
            metrics.published(batch.size());
            return batch.size();
        });
        return published == null ? 0 : published;
    }

    /** Sends the batch in order and waits until Kafka has acknowledged every message. */
    private void send(List<PendingEvent> batch)
    {
        List<CompletableFuture<?>> acknowledgements = new ArrayList<>();
        try {
            for (PendingEvent event : batch) {
                acknowledgements.add(publish(event));
            }
            CompletableFuture.allOf(acknowledgements.toArray(CompletableFuture[]::new))
                    .get(properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            metrics.publishFailed();
            throw new OutboxPublishException("Interrupted while publishing outbox events", exception);
        }
        catch (Exception exception) {
            metrics.publishFailed();
            throw new OutboxPublishException("Kafka did not acknowledge %d outbox event(s)".formatted(batch.size()), exception);
        }
    }

    /**
     * Sends one event as part of the trace of the request that produced it (the
     * outbox row carries that trace, see {@link Outbox}). The message, and
     * whatever its consumers do with it, then show up under that request.
     */
    private CompletableFuture<?> publish(PendingEvent event)
    {
        ProducerRecord<String, String> record = new ProducerRecord<>(event.topic(), event.key(), event.payload());
        record.headers().add(new RecordHeader(EVENT_TYPE_HEADER, event.eventType().getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader(EVENT_ID_HEADER, event.eventId().getBytes(StandardCharsets.UTF_8)));

        // The row is read like an incoming message: the trace it carries becomes the parent of this publish.
        ReceiverContext<Map<String, String>> context = new ReceiverContext<>(Map::get);
        context.setCarrier(event.traceParent() == null ? Map.of() : Map.of(Outbox.TRACE_PARENT, event.traceParent()));
        context.setRemoteServiceName("outbox");
        return Observation.createNotStarted("outbox.publish", () -> context, observations)
                .contextualName("outbox publish " + event.eventType())
                .lowCardinalityKeyValue("event.type", event.eventType())
                .observe(() -> kafka.send(record));
    }

    /** Published events are of no further use to the relay; they are kept for a while for troubleshooting. */
    @Scheduled(cron = "0 15 * * * *")
    void deleteOldPublishedEvents()
    {
        Timestamp cutoff = Timestamp.from(clock.instant().minus(properties.retention()));
        int deleted = jdbc.update("DELETE FROM outbox_events WHERE published_at < ?", cutoff);
        if (deleted > 0) {
            log.info("Deleted {} published outbox events older than {}", deleted, properties.retention());
        }
    }

    private static Duration min(Duration a, Duration b)
    {
        return a.compareTo(b) <= 0 ? a : b;
    }

    /** The batch could not be handed to Kafka; it stays in the outbox and will be sent again. */
    public static class OutboxPublishException extends RuntimeException
    {
        public OutboxPublishException(String message, Throwable cause)
        {
            super(message, cause);
        }
    }
}
