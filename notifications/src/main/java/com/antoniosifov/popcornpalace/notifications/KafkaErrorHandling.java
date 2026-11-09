package com.antoniosifov.popcornpalace.notifications;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

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
 */
@Configuration
class KafkaErrorHandling
{
    static final String DEAD_LETTER_SUFFIX = ".DLT";
    private static final int PARTITIONS = 3;

    @Bean
    DefaultErrorHandler errorHandler(KafkaOperations<String, String> kafka, NotificationProperties properties,
                                     MeterRegistry registry)
    {
        // Same partition number in the dead-letter topic, which is why it has as many partitions.
        DeadLetterPublishingRecoverer toDeadLetterTopic = new DeadLetterPublishingRecoverer(kafka,
                (message, exception) -> new TopicPartition(message.topic() + DEAD_LETTER_SUFFIX, message.partition()));

        // Every parked event is counted: a rising number is something to look at.
        Counter deadLetters = Counter.builder("popcorn.notifications.dead.letters")
                .description("Events moved to the dead-letter topic")
                .register(registry);
        ConsumerRecordRecoverer countedDeadLetters = (message, exception) -> {
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
}
