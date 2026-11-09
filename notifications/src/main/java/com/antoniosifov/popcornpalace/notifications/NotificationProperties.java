package com.antoniosifov.popcornpalace.notifications;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.time.ZoneId;

/**
 * Settings under {@code app.notifications} in application.yaml.
 *
 * @param from          sender shown on the emails
 * @param timeZone      showtimes are printed in this time zone
 * @param processedTtl  how long an event is remembered as handled
 * @param maxRetries    how many times a failed event is retried before it goes to the dead-letter topic
 * @param retryInterval pause between those retries
 */
@ConfigurationProperties("app.notifications")
public record NotificationProperties(
        String from,
        ZoneId timeZone,
        Duration processedTtl,
        int maxRetries,
        Duration retryInterval
)
{
}
