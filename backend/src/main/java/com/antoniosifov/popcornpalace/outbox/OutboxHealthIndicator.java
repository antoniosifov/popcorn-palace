package com.antoniosifov.popcornpalace.outbox;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Down when events have been stuck in the outbox for longer than
 * {@code app.outbox.stale-after}: bookings are being made, but nobody is
 * hearing about them. Not part of readiness, because the API itself is fine
 * and taking it out of service would help nobody.
 */
@Component
class OutboxHealthIndicator implements HealthIndicator
{
    private final OutboxMetrics metrics;
    private final OutboxProperties properties;

    OutboxHealthIndicator(OutboxMetrics metrics, OutboxProperties properties)
    {
        this.metrics = metrics;
        this.properties = properties;
    }

    @Override
    public Health health()
    {
        OutboxMetrics.Backlog backlog = metrics.backlog();
        boolean stuck = backlog.oldestAge().compareTo(properties.staleAfter()) > 0;
        return (stuck ? Health.down() : Health.up())
                .withDetail("pendingEvents", backlog.pending())
                .withDetail("oldestEventAgeSeconds", backlog.oldestAge().toSeconds())
                .build();
    }
}
