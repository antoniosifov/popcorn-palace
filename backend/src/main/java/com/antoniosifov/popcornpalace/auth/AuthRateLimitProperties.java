package com.antoniosifov.popcornpalace.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Settings under {@code app.rate-limit.auth}: how many login and registration
 * attempts one client address may make per window.
 */
@ConfigurationProperties("app.rate-limit.auth")
public record AuthRateLimitProperties(int maxRequests, Duration window)
{
}
