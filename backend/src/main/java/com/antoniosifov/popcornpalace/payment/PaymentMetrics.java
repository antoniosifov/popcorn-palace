package com.antoniosifov.popcornpalace.payment;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** What happens to payments, counted for the dashboard: charges, declines, provider failures, refunds, and the money involved. */
@Component
public class PaymentMetrics
{
    private static final String OUTCOMES = "popcorn.payments";

    private final Counter charged;
    private final Counter declined;
    private final Counter providerErrors;
    private final Counter refunded;
    private final DistributionSummary chargedAmount;
    private final DistributionSummary refundedAmount;

    public PaymentMetrics(MeterRegistry registry, PaymentProperties properties)
    {
        charged = outcome(registry, "charged");
        declined = outcome(registry, "declined");
        providerErrors = outcome(registry, "provider_error");
        refunded = outcome(registry, "refunded");
        chargedAmount = amount(registry, "popcorn.payments.charged.amount", "Money taken by successful charges", properties.currency());
        refundedAmount = amount(registry, "popcorn.payments.refunded.amount", "Money given back", properties.currency());
    }

    public void charged(BigDecimal amount)
    {
        charged.increment();
        chargedAmount.record(amount.doubleValue());
    }

    public void declined()
    {
        declined.increment();
    }

    public void providerError()
    {
        providerErrors.increment();
    }

    public void refunded(BigDecimal amount)
    {
        refunded.increment();
        refundedAmount.record(amount.doubleValue());
    }

    private static Counter outcome(MeterRegistry registry, String outcome)
    {
        return Counter.builder(OUTCOMES)
                .description("Payment attempts by outcome")
                .tag("outcome", outcome)
                .register(registry);
    }

    private static DistributionSummary amount(MeterRegistry registry, String name, String description, String currency)
    {
        return DistributionSummary.builder(name)
                .description(description)
                .tag("currency", currency)
                .register(registry);
    }
}
