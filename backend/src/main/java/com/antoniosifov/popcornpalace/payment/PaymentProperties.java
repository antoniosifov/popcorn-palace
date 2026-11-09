package com.antoniosifov.popcornpalace.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Settings under {@code app.payments} in application.yaml.
 *
 * @param currency               ISO 4217 code of the currency all prices are in
 * @param slowDelay              how long the sandbox provider takes for the "slow" test token
 * @param reconcileInterval      how often unfinished payments are looked at
 * @param abandonedAfter         a PENDING payment untouched for this long is considered abandoned
 * @param refundRetryAfter       a REFUND_PENDING payment untouched for this long gets its refund retried
 */
@ConfigurationProperties("app.payments")
public record PaymentProperties(
        String currency,
        Duration slowDelay,
        Duration reconcileInterval,
        Duration abandonedAfter,
        Duration refundRetryAfter
)
{
}
