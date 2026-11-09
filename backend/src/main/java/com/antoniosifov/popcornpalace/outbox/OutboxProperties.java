package com.antoniosifov.popcornpalace.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Settings under {@code app.outbox} in application.yaml.
 *
 * @param relayInterval how often events waiting in the outbox table are sent to Kafka
 * @param batchSize     most events sent in one go
 * @param sendTimeout   how long to wait for Kafka to acknowledge a batch
 * @param retention     published events are deleted after this long
 * @param staleAfter    the outbox reports itself unhealthy when an event has waited longer than this
 */
@ConfigurationProperties("app.outbox")
public record OutboxProperties(Duration relayInterval, int batchSize, Duration sendTimeout, Duration retention,
                               Duration staleAfter)
{
}
