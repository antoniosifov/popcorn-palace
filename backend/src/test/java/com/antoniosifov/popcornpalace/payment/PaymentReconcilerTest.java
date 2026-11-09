package com.antoniosifov.popcornpalace.payment;

import com.antoniosifov.popcornpalace.support.ApiIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What happens to payments the application did not get to finish, for
 * example because it was restarted in the middle of a checkout.
 */
class PaymentReconcilerTest extends ApiIntegrationTest
{
    private static final BigDecimal AMOUNT = new BigDecimal("29.00");

    @Autowired
    private PaymentReconciler reconciler;
    @Autowired
    private PaymentService paymentService;

    @DisplayName("A payment that was charged but never got its booking is refunded")
    @Test
    void refundsAChargeLeftWithoutABooking()
    {
        // The application recorded the payment, charged the customer, and then died.
        Payment payment = startPayment();
        paymentProvider.charge(payment.providerReference(), AMOUNT, "USD", FakePaymentProvider.TOKEN_OK);

        reconcileEverythingUnfinished();

        assertThat(paymentProvider.refundCount()).isEqualTo(1);
        assertThat(statusOf(payment)).containsEntry("status", "REFUNDED").containsEntry("failure_code", "ABANDONED");
    }

    @DisplayName("A payment that was recorded but never charged is simply closed")
    @Test
    void closesAPaymentThatWasNeverCharged()
    {
        // The application recorded the payment and died before reaching the provider.
        Payment payment = startPayment();

        reconcileEverythingUnfinished();

        assertThat(paymentProvider.refundCount()).isZero();
        assertThat(statusOf(payment)).containsEntry("status", "FAILED").containsEntry("failure_code", "ABANDONED");
    }

    @DisplayName("A payment still in progress is left alone until it is old enough")
    @Test
    void leavesRecentPaymentsAlone()
    {
        Payment payment = startPayment();
        paymentProvider.charge(payment.providerReference(), AMOUNT, "USD", FakePaymentProvider.TOKEN_OK);

        // "Old enough" means untouched for five minutes; this payment is a few milliseconds old.
        Instant fiveMinutesAgo = Instant.now().minusSeconds(300);
        reconciler.reconcile(fiveMinutesAgo, fiveMinutesAgo);

        assertThat(paymentProvider.refundCount()).isZero();
        assertThat(statusOf(payment)).containsEntry("status", "PENDING");
    }

    @DisplayName("Running the job again changes nothing")
    @Test
    void isSafeToRunTwice()
    {
        Payment payment = startPayment();
        paymentProvider.charge(payment.providerReference(), AMOUNT, "USD", FakePaymentProvider.TOKEN_OK);

        reconcileEverythingUnfinished();
        reconcileEverythingUnfinished();

        assertThat(paymentProvider.refundCount()).isEqualTo(1);
        assertThat(statusOf(payment)).containsEntry("status", "REFUNDED");
    }

    private Payment startPayment()
    {
        return paymentService.start(CUSTOMER_ID, 1L, UUID.randomUUID(), UUID.randomUUID().toString(), AMOUNT);
    }

    private Map<String, Object> statusOf(Payment payment)
    {
        return jdbc.queryForMap("SELECT status, failure_code FROM payments WHERE id = ?", payment.getId());
    }

    private void reconcileEverythingUnfinished()
    {
        Instant inTheFuture = Instant.now().plusSeconds(3600);
        reconciler.reconcile(inTheFuture, inTheFuture);
    }
}
