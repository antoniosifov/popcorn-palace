package com.antoniosifov.popcornpalace.notifications;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.TopicConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.time.Clock;
import java.time.Duration;

/**
 * What happens to an event that cannot be processed.
 *
 * <p>A consumer reads a partition in order, so one message that always fails
 * would block every message behind it forever. Instead:
 *
 * <ul>
 *   <li>a failure that might pass (the mail server is down for a moment) is
 *       retried a few times;</li>
 *   <li>a message that still fails, or can never succeed because it is
 *       malformed, is copied to a dead-letter topic and skipped, so the rest
 *       keep flowing and the bad one can be inspected and replayed later.</li>
 * </ul>
 *
 * <p>One kind of message is not kept: an account event, which can carry a link
 * that lets its holder set a password. A copy in a dead-letter topic would sit
 * there for as long as Kafka keeps messages, long after anybody needs it, so
 * such an event is dropped and counted instead. Nothing is lost by it: the
 * link is dead within the half hour, and the customer asks for another.
 */
@Configuration
class KafkaErrorHandling
{
    static final String DEAD_LETTER_SUFFIX = ".DLT";
    private static final int PARTITIONS = 3;
    private static final Logger log = LoggerFactory.getLogger(KafkaErrorHandling.class);

    @Bean
    DefaultErrorHandler errorHandler(KafkaOperations<String, String> kafka, NotificationProperties properties,
                                     MeterRegistry registry, @Value("${app.events.account-topic}") String accountTopic)
    {
        // Same partition number in the dead-letter topic, which is why it has as many partitions.
        DeadLetterPublishingRecoverer toDeadLetterTopic = new DeadLetterPublishingRecoverer(kafka,
                (message, exception) -> new TopicPartition(message.topic() + DEAD_LETTER_SUFFIX, message.partition()));

        // Every parked event is counted: a rising number is something to look at.
        Counter deadLetters = Counter.builder("popcorn.notifications.dead.letters")
                .description("Events moved to the dead-letter topic")
                .register(registry);
        Counter dropped = Counter.builder("popcorn.notifications.dropped")
                .description("Events that carried a secret and were dropped instead of parked")
                .register(registry);
        ConsumerRecordRecoverer countedDeadLetters = (message, exception) -> {
            if (accountTopic.equals(message.topic())) {
                dropped.increment();
                // Where it was and why, never what it said.
                log.warn("Dropped the account event at {}-{}@{}: {}", message.topic(), message.partition(),
                        message.offset(), rootCause(exception).getClass().getSimpleName());
                return;
            }
            deadLetters.increment();
            toDeadLetterTopic.accept(message, exception);
        };

        DefaultErrorHandler handler = new DefaultErrorHandler(countedDeadLetters,
                new FixedBackOff(properties.retryInterval().toMillis(), properties.maxRetries()));
        handler.addNotRetryableExceptions(InvalidEventException.class);
        return handler;
    }

    /**
     * Declared here as well as in the booking API, so this service can start
     * first. Creating a topic that already exists does nothing.
     */
    @Bean
    NewTopic bookingEventsTopic(@Value("${app.events.booking-topic}") String topic)
    {
        return TopicBuilder.name(topic).partitions(PARTITIONS).replicas(1).build();
    }

    @Bean
    NewTopic bookingEventsDeadLetterTopic(@Value("${app.events.booking-topic}") String topic)
    {
        return TopicBuilder.name(topic + DEAD_LETTER_SUFFIX).partitions(PARTITIONS).replicas(1).build();
    }

    /**
     * The same declaration as in the booking API, down to how long messages are
     * kept: they can carry a reset link, so an hour instead of Kafka's week.
     */
    @Bean
    NewTopic accountEventsTopic(@Value("${app.events.account-topic}") String topic,
                                @Value("${app.events.account-retention}") Duration retention)
    {
        return TopicBuilder.name(topic)
                .partitions(1)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(retention.toMillis()))
                .build();
    }

    @Bean
    Clock clock()
    {
        return Clock.systemUTC();
    }

    private static Throwable rootCause(Throwable exception)
    {
        Throwable cause = exception;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }
}
