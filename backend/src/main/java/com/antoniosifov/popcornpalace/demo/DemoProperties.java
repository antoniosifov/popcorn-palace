package com.antoniosifov.popcornpalace.demo;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.ZoneId;

/**
 * Settings under {@code app.demo} in application.yaml.
 *
 * @param enabled whether the demo programme is kept up at all
 * @param days    how many days ahead, counting today, have showtimes
 * @param zone    the time zone of the cinema: showtimes start at 11:00, 14:15 ... on its clock
 */
@ConfigurationProperties("app.demo")
public record DemoProperties(boolean enabled, int days, ZoneId zone)
{
}
