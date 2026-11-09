package com.antoniosifov.popcornpalace.payment;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A sandbox payment provider: no money moves, and the "card" is a test token
 * that decides the outcome, the way test tokens work at real providers.
 *
 * <table>
 *   <tr><td>{@code tok_visa}</td><td>charged</td></tr>
 *   <tr><td>{@code tok_slow}</td><td>charged after a delay</td></tr>
 *   <tr><td>{@code tok_declined}</td><td>declined: insufficient funds</td></tr>
 *   <tr><td>{@code tok_expired}</td><td>declined: expired card</td></tr>
 *   <tr><td>{@code tok_error}</td><td>the provider fails</td></tr>
 *   <tr><td>anything else</td><td>declined: invalid token</td></tr>
 * </table>
 *
 * It keeps what it "charged" in memory, so it behaves like a real provider
 * for repeated and concurrent calls: a reference is charged at most once and
 * refunded at most once.
 */
@Component
public class FakePaymentProvider implements PaymentProvider
{
    public static final String TOKEN_OK = "tok_visa";
    public static final String TOKEN_SLOW = "tok_slow";
    public static final String TOKEN_DECLINED = "tok_declined";
    public static final String TOKEN_EXPIRED = "tok_expired";
    public static final String TOKEN_ERROR = "tok_error";

    private final Map<String, ChargeState> charges = new ConcurrentHashMap<>();
    private final AtomicInteger chargeCount = new AtomicInteger();
    private final AtomicInteger refundCount = new AtomicInteger();
    private final AtomicBoolean refundsFailing = new AtomicBoolean();
    private final Duration slowDelay;

    public FakePaymentProvider(PaymentProperties properties)
    {
        this.slowDelay = properties.slowDelay();
    }

    @Override
    public ChargeResult charge(String reference, BigDecimal amount, String currency, String paymentToken)
    {
        if (charges.containsKey(reference)) {
            // Same reference again: report the earlier result, charge nothing new.
            return new ChargeResult.Charged();
        }
        switch (paymentToken == null ? "" : paymentToken) {
            case TOKEN_OK -> {
                // falls through to the charge below
            }
            case TOKEN_SLOW -> sleep(slowDelay);
            case TOKEN_DECLINED -> {
                return new ChargeResult.Declined("insufficient_funds");
            }
            case TOKEN_EXPIRED -> {
                return new ChargeResult.Declined("expired_card");
            }
            case TOKEN_ERROR -> throw new PaymentProviderException("The payment provider is not responding");
            default -> {
                return new ChargeResult.Declined("invalid_token");
            }
        }
        // putIfAbsent makes "charge once per reference" hold for simultaneous calls too.
        if (charges.putIfAbsent(reference, ChargeState.CHARGED) == null) {
            chargeCount.incrementAndGet();
        }
        return new ChargeResult.Charged();
    }

    @Override
    public void refund(String reference)
    {
        if (refundsFailing.get()) {
            throw new PaymentProviderException("The payment provider is not responding");
        }
        // Only a charged reference becomes refunded; refunding again changes nothing.
        if (charges.replace(reference, ChargeState.CHARGED, ChargeState.REFUNDED)) {
            refundCount.incrementAndGet();
        }
    }

    @Override
    public Optional<ChargeState> find(String reference)
    {
        return Optional.ofNullable(charges.get(reference));
    }

    // --- controls and counters used by tests and demos; a real provider has a dashboard for this ---

    /** While true, every refund fails as if the provider were down. */
    public void setRefundsFailing(boolean failing)
    {
        refundsFailing.set(failing);
    }

    /** How many distinct references have been charged. */
    public int chargeCount()
    {
        return chargeCount.get();
    }

    public int refundCount()
    {
        return refundCount.get();
    }

    /** Forgets everything, as if the sandbox account were new. */
    public void reset()
    {
        charges.clear();
        chargeCount.set(0);
        refundCount.set(0);
        refundsFailing.set(false);
    }

    private static void sleep(Duration duration)
    {
        try {
            Thread.sleep(duration.toMillis());
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PaymentProviderException("Interrupted while waiting for the payment provider");
        }
    }
}
