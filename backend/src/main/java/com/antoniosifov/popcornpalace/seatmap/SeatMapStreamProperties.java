package com.antoniosifov.popcornpalace.seatmap;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Settings under {@code app.seatmap} in application.yaml.
 *
 * @param streamTimeout     an event stream is closed after this long; the browser reconnects by itself
 * @param heartbeatInterval how often a comment is sent to every open stream, to keep connections alive and notice dead ones
 */
@ConfigurationProperties("app.seatmap")
public record SeatMapStreamProperties(Duration streamTimeout, Duration heartbeatInterval)
{
}
