package com.antoniosifov.popcornpalace.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Plain unit tests of the payment's allowed state changes and of the sandbox provider. */
class PaymentTest
{
    private static final Instant NOW = Instant.parse("2030-01-10T12:00:00Z");
    private static final BigDecimal AMOUNT = new BigDecimal("29.00");

    @DisplayName("A payment starts PENDING and follows the paid, cancelled, refunded path")
    @Test
    void followsTheHappyPathAndCancellation()
    {
        Payment payment = newPayment();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);

        payment.markSucceeded(NOW);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);

        payment.markRefundPending(NOW);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUND_PENDING);
        assertThat(payment.getFailureCode()).isEqualTo(Payment.BOOKING_CANCELLED);

        payment.markRefunded(Payment.BOOKING_CANCELLED, NOW);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @DisplayName("State changes that make no sense are refused")
    @Test
    void refusesImpossibleTransitions()
    {
        Payment failed = newPayment();
        failed.markFailed("insufficient_funds", NOW);
        assertThatThrownBy(() -> failed.markSucceeded(NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> failed.markRefunded("x", NOW)).isInstanceOf(IllegalStateException.class);

        Payment succeeded = newPayment();
        succeeded.markSucceeded(NOW);
        assertThatThrownBy(() -> succeeded.markFailed("x", NOW)).isInstanceOf(IllegalStateException.class);
        // A paid booking is refunded only through a cancellation, which records that a refund is owed first.
        assertThatThrownBy(() -> succeeded.markRefunded("x", NOW)).isInstanceOf(IllegalStateException.class);

        Payment refunded = newPayment();
        refunded.markRefunded(Payment.BOOKING_FAILED, NOW);
        assertThatThrownBy(() -> refunded.markRefunded(Payment.BOOKING_FAILED, NOW)).isInstanceOf(IllegalStateException.class);
    }

    @DisplayName("The sandbox provider charges a reference once, however often it is asked")
    @Test
    void providerChargesAReferenceOnce()
    {
        FakePaymentProvider provider = provider();

        provider.charge("ref-1", AMOUNT, "USD", FakePaymentProvider.TOKEN_OK);
        provider.charge("ref-1", AMOUNT, "USD", FakePaymentProvider.TOKEN_OK);
        provider.charge("ref-2", AMOUNT, "USD", FakePaymentProvider.TOKEN_OK);

        assertThat(provider.chargeCount()).isEqualTo(2);
        assertThat(provider.find("ref-1")).contains(PaymentProvider.ChargeState.CHARGED);
        assertThat(provider.find("never-charged")).isEmpty();
    }

    @DisplayName("The sandbox provider refunds a charge once and ignores references it never charged")
    @Test
    void providerRefundsOnce()
    {
        FakePaymentProvider provider = provider();
        provider.charge("ref-1", AMOUNT, "USD", FakePaymentProvider.TOKEN_OK);

        provider.refund("ref-1");
        provider.refund("ref-1");
        provider.refund("never-charged");

        assertThat(provider.refundCount()).isEqualTo(1);
        assertThat(provider.find("ref-1")).contains(PaymentProvider.ChargeState.REFUNDED);
    }

    @DisplayName("Each sandbox token produces its documented outcome")
    @Test
    void providerTokensBehaveAsDocumented()
    {
        FakePaymentProvider provider = provider();

        assertThat(provider.charge("a", AMOUNT, "USD", FakePaymentProvider.TOKEN_OK))
                .isInstanceOf(PaymentProvider.ChargeResult.Charged.class);
        assertThat(provider.charge("b", AMOUNT, "USD", FakePaymentProvider.TOKEN_SLOW))
                .isInstanceOf(PaymentProvider.ChargeResult.Charged.class);
        assertThat(provider.charge("c", AMOUNT, "USD", FakePaymentProvider.TOKEN_DECLINED))
                .isEqualTo(new PaymentProvider.ChargeResult.Declined("insufficient_funds"));
        assertThat(provider.charge("d", AMOUNT, "USD", FakePaymentProvider.TOKEN_EXPIRED))
                .isEqualTo(new PaymentProvider.ChargeResult.Declined("expired_card"));
        assertThat(provider.charge("e", AMOUNT, "USD", "4242424242424242"))
                .isEqualTo(new PaymentProvider.ChargeResult.Declined("invalid_token"));
        assertThatThrownBy(() -> provider.charge("f", AMOUNT, "USD", FakePaymentProvider.TOKEN_ERROR))
                .isInstanceOf(PaymentProviderException.class);

        // Only the two successful tokens moved any "money".
        assertThat(provider.chargeCount()).isEqualTo(2);
    }

    private static Payment newPayment()
    {
        return new Payment(UUID.randomUUID(), 1L, UUID.randomUUID(), "key-12345678", AMOUNT, "USD", NOW);
    }

    private static FakePaymentProvider provider()
    {
        return new FakePaymentProvider(new PaymentProperties("USD", Duration.ofMillis(10),
                Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofSeconds(30)));
    }
}
