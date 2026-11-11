package com.antoniosifov.popcornpalace.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Settings under {@code app.password-reset}.
 *
 * @param ttl       how long the link in the email works
 * @param maxEmails how many emails one account is sent per window, whoever asks
 * @param window    the length of that window
 */
@ConfigurationProperties("app.password-reset")
public record PasswordResetProperties(Duration ttl, int maxEmails, Duration window)
{
}
