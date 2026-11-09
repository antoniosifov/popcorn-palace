package com.antoniosifov.popcornpalace.payment;

/**
 * Life of a payment. The allowed values are also listed in the database
 * CHECK constraint ck_payments_status.
 *
 * <pre>
 * PENDING ──► SUCCEEDED ──► REFUND_PENDING ──► REFUNDED
 *    │                                            ▲
 *    ├──► FAILED                                  │
 *    └──► (charged, but the booking failed) ──────┘
 * </pre>
 */
public enum PaymentStatus
{
    /** Recorded, and the provider is being asked (or the application stopped before learning the answer). */
    PENDING,
    /** The customer was charged and has a booking for it. */
    SUCCEEDED,
    /** The customer was not charged: the card was declined or the provider could not be reached. */
    FAILED,
    /** The money must go back (the booking was cancelled), and the refund has not been confirmed yet. */
    REFUND_PENDING,
    /** The money went back. */
    REFUNDED
}
