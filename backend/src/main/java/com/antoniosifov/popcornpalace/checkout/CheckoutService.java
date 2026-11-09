package com.antoniosifov.popcornpalace.checkout;

import com.antoniosifov.popcornpalace.booking.BookingResponse;
import com.antoniosifov.popcornpalace.booking.BookingService;
import com.antoniosifov.popcornpalace.common.error.ApiException;
import com.antoniosifov.popcornpalace.common.error.ErrorCode;
import com.antoniosifov.popcornpalace.hold.HoldProperties;
import com.antoniosifov.popcornpalace.hold.HoldRecord;
import com.antoniosifov.popcornpalace.hold.SeatHoldStore;
import com.antoniosifov.popcornpalace.payment.Payment;
import com.antoniosifov.popcornpalace.payment.PaymentProvider;
import com.antoniosifov.popcornpalace.payment.PaymentProvider.ChargeResult;
import com.antoniosifov.popcornpalace.payment.PaymentProviderException;
import com.antoniosifov.popcornpalace.payment.PaymentService;
import com.antoniosifov.popcornpalace.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Pays for a hold and turns it into a booking.
 *
 * <p>Three systems are involved (Redis for the hold, the payment provider for
 * the money, PostgreSQL for the booking) and no transaction spans them. The
 * steps are therefore ordered so that every way of stopping halfway is either
 * harmless or repairable:
 *
 * <ol>
 *   <li>record the payment as PENDING and commit;</li>
 *   <li>charge the customer (no database transaction is open during this call);</li>
 *   <li>save the booking and mark the payment SUCCEEDED in one transaction;</li>
 *   <li>release the hold.</li>
 * </ol>
 *
 * If step 3 fails after step 2 succeeded, the charge is refunded right away.
 * If the application dies between them, the PENDING payment is found and
 * refunded later by {@code PaymentReconciler}.
 */
@Service
public class CheckoutService
{
    private static final Logger log = LoggerFactory.getLogger(CheckoutService.class);

    /** The result, and whether it was produced now or is the stored answer to an earlier identical request. */
    public record Result(BookingResponse booking, boolean replayed)
    {
    }

    private final SeatHoldStore holdStore;
    private final HoldProperties holdProperties;
    private final CheckoutLock checkoutLock;
    private final PaymentService paymentService;
    private final PaymentProvider paymentProvider;
    private final BookingService bookingService;

    public CheckoutService(SeatHoldStore holdStore, HoldProperties holdProperties, CheckoutLock checkoutLock,
                           PaymentService paymentService, PaymentProvider paymentProvider, BookingService bookingService)
    {
        this.holdStore = holdStore;
        this.holdProperties = holdProperties;
        this.checkoutLock = checkoutLock;
        this.paymentService = paymentService;
        this.paymentProvider = paymentProvider;
        this.bookingService = bookingService;
    }

    /**
     * @param idempotencyKey chosen by the client, one per purchase attempt. Sending the
     *                       same request again with the same key returns the first
     *                       outcome instead of charging again.
     */
    public Result confirm(Long showtimeId, UUID holdId, String idempotencyKey, CheckoutRequest request, CurrentUser user)
    {
        // Seen this key before? Then this is a retry: answer from what is recorded.
        var earlier = paymentService.findByIdempotencyKey(user.id(), idempotencyKey);
        if (earlier.isPresent()) {
            return replay(earlier.get(), holdId);
        }

        HoldRecord hold = holdStore.find(showtimeId, holdId)
                .filter(found -> found.userId().equals(user.id()))
                .orElseThrow(CheckoutService::holdNotFound);

        CheckoutLock.Handle lock = checkoutLock.tryAcquire(showtimeId, holdId)
                .orElseThrow(CheckoutService::paymentInProgress);
        try {
            return new Result(payAndBook(hold, idempotencyKey, request.paymentToken(), user), false);
        }
        finally {
            releaseQuietly(lock);
        }
    }

    private BookingResponse payAndBook(HoldRecord hold, String idempotencyKey, String paymentToken, CurrentUser user)
    {
        // The hold must outlive the payment: if it expired while the customer
        // was being charged, someone else could take the seats.
        if (!holdStore.keepAlive(hold, holdProperties.confirmGrace())) {
            throw holdNotFound();
        }

        // 1. Recorded and committed before any money moves.
        Payment payment = paymentService.start(user.id(), hold.showtimeId(), hold.id(), idempotencyKey, hold.totalPrice());

        // 2. Charge. The payment id is the reference, so the provider itself
        //    would not charge twice for it.
        ChargeResult charge;
        try {
            charge = paymentProvider.charge(payment.providerReference(), payment.getAmount(), payment.getCurrency(), paymentToken);
        }
        catch (PaymentProviderException exception) {
            paymentService.markFailed(payment.getId(), Payment.PROVIDER_ERROR);
            throw providerUnavailable();
        }
        if (charge instanceof ChargeResult.Declined declined) {
            // The hold is kept: the customer can try another card with a new key.
            paymentService.markFailed(payment.getId(), declined.declineCode());
            throw paymentDeclined(declined.declineCode());
        }

        // 3. The customer has paid. Save the booking, or give the money back.
        BookingResponse booking;
        try {
            Map<Long, BigDecimal> pricesBySeat = new LinkedHashMap<>();
            hold.seats().forEach(seat -> pricesBySeat.put(seat.seatId(), seat.price()));
            booking = bookingService.create(user.id(), hold.showtimeId(), pricesBySeat, payment.getId());
        }
        catch (RuntimeException bookingFailure) {
            if (!paymentService.refund(payment.getId(), Payment.BOOKING_FAILED)) {
                // Still PENDING and charged; the reconciliation job refunds it.
                log.error("Payment {} was charged, the booking failed and the refund failed too; "
                        + "it will be refunded by the reconciliation job", payment.getId());
            }
            throw bookingFailure;
        }

        // 4. The seats are sold; the hold has done its job.
        try {
            holdStore.release(hold);
        }
        catch (DataAccessException exception) {
            // Not an error for the customer: the leftover keys expire on their own,
            // and the seat map reads sold seats from the database.
            log.warn("Booking {} is saved, but hold {} could not be released; it will expire", booking.id(), hold.id(), exception);
        }
        return booking;
    }

    /** Answers a repeated request from the recorded payment, without touching the provider. */
    private Result replay(Payment payment, UUID holdId)
    {
        if (!payment.getHoldId().equals(holdId)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.IDEMPOTENCY_KEY_REUSED,
                    "This Idempotency-Key was already used for a different purchase; use a new key for each purchase");
        }
        return switch (payment.getStatus()) {
            case SUCCEEDED, REFUND_PENDING -> new Result(bookingService.getByPayment(payment.getId()), true);
            case PENDING -> throw paymentInProgress();
            case FAILED -> throw Payment.PROVIDER_ERROR.equals(payment.getFailureCode())
                    ? providerUnavailable()
                    : paymentDeclined(payment.getFailureCode());
            case REFUNDED -> {
                // Either the booking could not be completed, or it was completed and cancelled later.
                var booking = bookingService.findByPayment(payment.getId());
                if (booking.isPresent()) {
                    yield new Result(booking.get(), true);
                }
                throw ApiException.conflict(ErrorCode.PAYMENT_REFUNDED,
                        "The payment was refunded because the booking could not be completed; start again with a new key");
            }
        };
    }

    private void releaseQuietly(CheckoutLock.Handle lock)
    {
        try {
            checkoutLock.release(lock);
        }
        catch (DataAccessException exception) {
            log.warn("Checkout lock {} could not be released; it will expire", lock.key(), exception);
        }
    }

    /** One answer for "never existed", "expired" and "belongs to someone else". */
    private static ApiException holdNotFound()
    {
        return ApiException.notFound(ErrorCode.HOLD_NOT_FOUND, "The hold does not exist or has expired");
    }

    private static ApiException paymentInProgress()
    {
        return ApiException.conflict(ErrorCode.PAYMENT_IN_PROGRESS,
                "A payment for this purchase is already being processed; wait for it to finish");
    }

    private static ApiException paymentDeclined(String declineCode)
    {
        return new ApiException(HttpStatus.PAYMENT_REQUIRED, ErrorCode.PAYMENT_DECLINED,
                "The payment was declined. Your seats are still held; try another payment method")
                .with("declineCode", declineCode);
    }

    private static ApiException providerUnavailable()
    {
        return new ApiException(HttpStatus.BAD_GATEWAY, ErrorCode.PAYMENT_PROVIDER_UNAVAILABLE,
                "The payment provider is not responding. You were not charged; try again with a new key");
    }
}
