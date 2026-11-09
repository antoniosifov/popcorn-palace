package com.antoniosifov.popcornpalace.payment;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * The outside service that actually moves money. The application only talks
 * to this interface, so a real provider can replace the sandbox one
 * ({@link FakePaymentProvider}) without touching the checkout code.
 *
 * <p>Every call names the payment by a {@code reference} chosen by the
 * application. Providers treat a repeated call with the same reference as the
 * same payment, which is what makes retrying after a timeout safe.
 */
public interface PaymentProvider
{
    /** What the provider answered to a charge. */
    sealed interface ChargeResult
    {
        record Charged() implements ChargeResult
        {
        }

        /** The provider refused the payment method, for example for insufficient funds. Nothing was charged. */
        record Declined(String declineCode) implements ChargeResult
        {
        }
    }

    /** What the provider currently knows about a reference. */
    enum ChargeState
    {
        CHARGED,
        REFUNDED
    }

    /**
     * Charges the amount, or returns the earlier result if this reference was already charged.
     *
     * @param paymentToken stands in for the customer's card; card numbers never reach this application
     * @throws PaymentProviderException if the provider cannot be reached or fails; the charge did not happen
     */
    ChargeResult charge(String reference, BigDecimal amount, String currency, String paymentToken);

    /**
     * Returns the money of a charged reference. Refunding twice is the same as refunding once.
     *
     * @throws PaymentProviderException if the provider cannot be reached or fails; the refund may be retried
     */
    void refund(String reference);

    /** Looks a reference up; empty if the provider never charged it. Used to settle payments left unfinished. */
    Optional<ChargeState> find(String reference);
}
