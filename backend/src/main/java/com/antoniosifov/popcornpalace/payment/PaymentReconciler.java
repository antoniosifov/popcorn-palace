package com.antoniosifov.popcornpalace.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * Finishes payments that were left half-done. Two things can leave a payment
 * unfinished, and neither can be prevented, only repaired:
 *
 * <ul>
 *   <li>the application stopped between charging the customer and saving the
 *       booking, leaving a PENDING payment that may or may not have been charged;</li>
 *   <li>a booking was cancelled, but the provider was down when the refund was
 *       sent, leaving a REFUND_PENDING payment.</li>
 * </ul>
 *
 * Every step here is safe to repeat (the provider treats a repeated refund as
 * one refund, and a payment's state only moves forward), so it does not matter
 * if two instances of the application run this at the same time.
 */
@Component
public class PaymentReconciler
{
    private static final Logger log = LoggerFactory.getLogger(PaymentReconciler.class);

    private final PaymentRepository paymentRepository;
    private final PaymentProvider paymentProvider;
    private final PaymentService paymentService;
    private final PaymentProperties properties;
    private final Clock clock;

    public PaymentReconciler(PaymentRepository paymentRepository, PaymentProvider paymentProvider,
                             PaymentService paymentService, PaymentProperties properties, Clock clock)
    {
        this.paymentRepository = paymentRepository;
        this.paymentProvider = paymentProvider;
        this.paymentService = paymentService;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.payments.reconcile-interval}", initialDelayString = "${app.payments.reconcile-interval}")
    void runOnSchedule()
    {
        Instant now = clock.instant();
        reconcile(now.minus(properties.abandonedAfter()), now.minus(properties.refundRetryAfter()));
    }

    /**
     * @param pendingUntouchedSince       PENDING payments not updated since this moment are treated as abandoned
     * @param refundPendingUntouchedSince REFUND_PENDING payments not updated since this moment get their refund retried
     */
    public void reconcile(Instant pendingUntouchedSince, Instant refundPendingUntouchedSince)
    {
        for (Payment payment : paymentRepository.findTop100ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                PaymentStatus.PENDING, pendingUntouchedSince)) {
            settleAbandoned(payment);
        }
        for (Payment payment : paymentRepository.findTop100ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                PaymentStatus.REFUND_PENDING, refundPendingUntouchedSince)) {
            paymentService.refund(payment.getId(), payment.getFailureCode());
        }
    }

    /** Nobody is waiting for this payment any more, and its hold has expired: make sure the customer is not out of pocket. */
    private void settleAbandoned(Payment payment)
    {
        try {
            if (paymentProvider.find(payment.providerReference()).isPresent()) {
                // The provider did charge. There is no booking to show for it, so the money goes back.
                if (paymentService.refund(payment.getId(), Payment.ABANDONED)) {
                    log.warn("Refunded abandoned payment {} that had been charged without a booking", payment.getId());
                }
            }
            else {
                paymentService.markFailed(payment.getId(), Payment.ABANDONED);
                log.info("Closed abandoned payment {}; it was never charged", payment.getId());
            }
        }
        catch (RuntimeException exception) {
            // One bad payment must not stop the others; it is picked up again on the next run.
            log.warn("Could not settle abandoned payment {}", payment.getId(), exception);
        }
    }

    @Configuration
    @EnableScheduling
    static class SchedulingConfig
    {
    }
}
