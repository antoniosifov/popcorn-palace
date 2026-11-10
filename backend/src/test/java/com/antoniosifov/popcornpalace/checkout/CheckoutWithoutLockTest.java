package com.antoniosifov.popcornpalace.checkout;

import com.antoniosifov.popcornpalace.booking.BookingService;
import com.antoniosifov.popcornpalace.hold.HoldProperties;
import com.antoniosifov.popcornpalace.hold.SeatHoldStore;
import com.antoniosifov.popcornpalace.payment.FakePaymentProvider;
import com.antoniosifov.popcornpalace.payment.PaymentMetrics;
import com.antoniosifov.popcornpalace.payment.PaymentService;
import com.antoniosifov.popcornpalace.security.CurrentUser;
import com.antoniosifov.popcornpalace.support.ApiIntegrationTest;
import com.antoniosifov.popcornpalace.support.SimultaneousRequests;
import com.antoniosifov.popcornpalace.user.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The checkout lock is the first line of defence against a double charge, and
 * it lives in Redis, which may lose it. This test removes the lock entirely
 * and checks the second line: several requests do charge the customer for the
 * same seats, the database lets exactly one of them create the booking, and
 * every other charge is refunded.
 */
class CheckoutWithoutLockTest extends ApiIntegrationTest
{
    private static final int SIMULTANEOUS_REQUESTS = 8;

    @Autowired
    private SeatHoldStore holdStore;
    @Autowired
    private HoldProperties holdProperties;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private BookingService bookingService;
    @Autowired
    private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;
    @Autowired
    private PaymentMetrics paymentMetrics;

    @DisplayName("Without the lock, simultaneous payments for one hold end as one booking and one net charge")
    @Test
    void everyChargeButOneIsRefunded() throws Exception
    {
        long movieId = createMovie("Interstellar");
        long hallId = createHall("Hall 1");
        long showtimeId = createShowtime(movieId, hallId, "2030-01-15T18:00:00Z", "2030-01-15T20:49:00Z");
        UUID holdId = UUID.fromString(createHold(customerToken, showtimeId, seatId(hallId, 1, 1), seatId(hallId, 1, 2)));

        CheckoutService withoutLock = new CheckoutService(holdStore, holdProperties, new AlwaysFreeLock(),
                paymentService, paymentProvider, paymentMetrics, bookingService);
        CurrentUser customer = new CurrentUser(CUSTOMER_ID, Role.CUSTOMER);
        CheckoutRequest slowCard = new CheckoutRequest(FakePaymentProvider.TOKEN_SLOW);

        List<String> outcomes = SimultaneousRequests.run(SIMULTANEOUS_REQUESTS, () -> {
            try {
                withoutLock.confirm(showtimeId, holdId, UUID.randomUUID().toString(), slowCard, customer);
                return "booked";
            }
            catch (RuntimeException exception) {
                return "failed";
            }
        });

        // The database let exactly one booking through ...
        assertThat(outcomes).filteredOn("booked"::equals).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bookings", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM booking_seats", Long.class)).isEqualTo(2);

        // ... more than one request really did charge the customer (otherwise this test proves nothing) ...
        assertThat(paymentProvider.chargeCount()).isGreaterThan(1);

        // ... and every charge except the winner's was given back.
        assertThat(paymentProvider.chargeCount() - paymentProvider.refundCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payments WHERE status = 'SUCCEEDED'", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payments WHERE status = 'REFUNDED'", Long.class))
                .isEqualTo((long) paymentProvider.chargeCount() - 1);
        // Nothing is left half-done for the reconciliation job.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payments WHERE status = 'PENDING'", Long.class)).isZero();
    }

    /** A lock that never refuses, as if the real one had been lost. */
    private class AlwaysFreeLock extends CheckoutLock
    {
        AlwaysFreeLock()
        {
            super(redisTemplate);
        }

        @Override
        Optional<Handle> tryAcquire(long showtimeId, UUID holdId)
        {
            return Optional.of(new Handle("no-lock:" + UUID.randomUUID(), "nobody"));
        }

        @Override
        void release(Handle handle)
        {
        }
    }
}
