package com.antoniosifov.popcornpalace.hold;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Settings under {@code app.holds} in application.yaml.
 *
 * @param ttl          how long seats stay reserved before the customer must have confirmed
 * @param maxSeats     most seats one customer can hold for one showtime
 * @param confirmGrace while a hold is being turned into a booking it is kept alive at least this long
 */
@ConfigurationProperties("app.holds")
public record HoldProperties(Duration ttl, int maxSeats, Duration confirmGrace)
{
}
