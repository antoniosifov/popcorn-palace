package com.antoniosifov.popcornpalace.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Records what happens to payments. Each method is one short database
 * transaction, and none of them is open while the payment provider is being
 * called: a slow provider must not hold database connections, and what the
 * provider did must be recorded even if a later step fails.
 */
@Service
@EnableConfigurationProperties(PaymentProperties.class)
public class PaymentService
{
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final PaymentProvider paymentProvider;
    private final PaymentProperties properties;
    private final PaymentMetrics metrics;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public PaymentService(PaymentRepository paymentRepository, PaymentProvider paymentProvider,
                          PaymentProperties properties, PaymentMetrics metrics,
                          PlatformTransactionManager transactionManager, Clock clock)
    {
        this.paymentRepository = paymentRepository;
        this.paymentProvider = paymentProvider;
        this.properties = properties;
        this.metrics = metrics;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public Optional<Payment> findByIdempotencyKey(UUID userId, String idempotencyKey)
    {
        return paymentRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
    }

    /**
     * Records a new payment as PENDING and commits it before any money moves.
     * If the same user already used this idempotency key, the unique constraint
     * uq_payments_user_idempotency_key rejects the insert.
     */
    public Payment start(UUID userId, Long showtimeId, UUID holdId, String idempotencyKey, BigDecimal amount)
    {
        Payment payment = new Payment(userId, showtimeId, holdId, idempotencyKey, amount, properties.currency(), now());
        return transaction.execute(status -> paymentRepository.saveAndFlush(payment));
    }

    public void markFailed(UUID paymentId, String failureCode)
    {
        update(paymentId, (payment, now) -> payment.markFailed(failureCode, now));
    }

    public void markRefundPending(UUID paymentId)
    {
        update(paymentId, Payment::markRefundPending);
    }

    /**
     * Returns the money and records that it went back. If the provider fails,
     * nothing is recorded and false is returned; the caller decides what to do
     * (for cancellations, the reconciliation job simply tries again later).
     */
    public boolean refund(UUID paymentId, String reason)
    {
        try {
            paymentProvider.refund(paymentId.toString());
        }
        catch (PaymentProviderException exception) {
            log.warn("Refund of payment {} failed and will be retried: {}", paymentId, exception.getMessage());
            return false;
        }
        update(paymentId, (payment, now) -> {
            payment.markRefunded(reason, now);
            metrics.refunded(payment.getAmount());
        });
        return true;
    }

    private void update(UUID paymentId, BiConsumer<Payment, Instant> change)
    {
        transaction.executeWithoutResult(status -> {
            Payment payment = paymentRepository.findById(paymentId)
                    .orElseThrow(() -> new IllegalStateException("Payment %s does not exist".formatted(paymentId)));
            change.accept(payment, now());
        });
    }

    private Instant now()
    {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }
}
