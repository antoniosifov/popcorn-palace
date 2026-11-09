package com.antoniosifov.popcornpalace.common.observability;

import io.micrometer.observation.ObservationPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.ServerRequestObservationContext;

/**
 * What is worth a trace. Every API request, every message sent to Kafka and
 * every outbox publish is observed: timed for the metrics and recorded as a
 * span. Two sources of noise are left out, because they would drown the rest.
 */
@Configuration
class ObservabilityConfig
{
    /** Spring observes every run of a {@code @Scheduled} method under this name. */
    private static final String SCHEDULED_TASKS = "tasks.scheduled";
    private static final String ACTUATOR = "/actuator";

    @Bean
    ObservationPredicate noNoiseObservations()
    {
        return (name, context) -> {
            // The outbox relay runs twice a second and the seat map heartbeat every
            // few seconds; a trace per run would say nothing. What they do when
            // there is work (publishing an event) is traced on its own.
            if (name.startsWith(SCHEDULED_TASKS)) {
                return false;
            }
            // Prometheus scrapes and health checks are not user traffic.
            return !(context instanceof ServerRequestObservationContext request
                    && request.getCarrier().getRequestURI().startsWith(ACTUATOR));
        };
    }
}
