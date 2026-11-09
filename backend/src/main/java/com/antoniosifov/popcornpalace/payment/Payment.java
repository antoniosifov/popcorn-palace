package com.antoniosifov.popcornpalace.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One attempt to pay for a hold. Its id doubles as the reference sent to the
 * payment provider, so the provider can recognize a repeated request for the
 * same payment.
 */
@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // required by JPA, not for application code
public class Payment
{
    /** Failure code used when the provider could not be reached or answered with an error. */
    public static final String PROVIDER_ERROR = "PROVIDER_ERROR";
    /** The customer was charged, but the booking could not be saved, so the money was returned. */
    public static final String BOOKING_FAILED = "BOOKING_FAILED";
    /** The application stopped in the middle of this payment and the job cleaned it up later. */
    public static final String ABANDONED = "ABANDONED";
    /** The booking was cancelled, so the money goes back. */
    public static final String BOOKING_CANCELLED = "BOOKING_CANCELLED";

    /** Assigned by the application, so it is known before the row is saved. */
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "showtime_id", nullable = false, updatable = false)
    private Long showtimeId;

    @Column(name = "hold_id", nullable = false, updatable = false)
    private UUID holdId;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 100)
    private String idempotencyKey;

    @Column(nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, updatable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "failure_code", length = 50)
    private String failureCode;

    /** Optimistic lock: an update based on stale data fails instead of overwriting a newer state. */
    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Payment(UUID userId, Long showtimeId, UUID holdId, String idempotencyKey,
                   BigDecimal amount, String currency, Instant now)
    {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.showtimeId = showtimeId;
        this.holdId = holdId;
        this.idempotencyKey = idempotencyKey;
        this.amount = amount;
        this.currency = currency;
        this.status = PaymentStatus.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** The reference this payment is known by at the payment provider. */
    public String providerReference()
    {
        return id.toString();
    }

    public void markSucceeded(Instant now)
    {
        moveTo(PaymentStatus.SUCCEEDED, null, now, PaymentStatus.PENDING);
    }

    public void markFailed(String failureCode, Instant now)
    {
        moveTo(PaymentStatus.FAILED, failureCode, now, PaymentStatus.PENDING);
    }

    public void markRefundPending(Instant now)
    {
        moveTo(PaymentStatus.REFUND_PENDING, BOOKING_CANCELLED, now, PaymentStatus.SUCCEEDED);
    }

    /** From PENDING when a charge is undone right away, from REFUND_PENDING when a cancellation's refund completes. */
    public void markRefunded(String reason, Instant now)
    {
        moveTo(PaymentStatus.REFUNDED, reason, now, PaymentStatus.PENDING, PaymentStatus.REFUND_PENDING);
    }

    /** Refuses transitions that make no sense, so a bug cannot, say, refund a failed payment. */
    private void moveTo(PaymentStatus next, String failureCode, Instant now, PaymentStatus... allowedFrom)
    {
        for (PaymentStatus allowed : allowedFrom) {
            if (status == allowed) {
                this.status = next;
                this.failureCode = failureCode;
                this.updatedAt = now;
                return;
            }
        }
        throw new IllegalStateException("Payment %s cannot go from %s to %s".formatted(id, status, next));
    }
}
