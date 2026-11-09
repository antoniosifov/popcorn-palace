package com.antoniosifov.popcornpalace.booking;

import com.antoniosifov.popcornpalace.payment.PaymentReconciler;
import com.antoniosifov.popcornpalace.support.ApiIntegrationTest;
import com.antoniosifov.popcornpalace.support.SimultaneousRequests;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BookingCancellationApiTest extends ApiIntegrationTest
{
    @Autowired
    private PaymentReconciler reconciler;

    private long hallId;
    private long showtimeId;
    private long seatA;
    private long seatB;

    @BeforeEach
    void createShowtime() throws Exception
    {
        long movieId = createMovie("Interstellar");
        hallId = createHall("Hall 1");
        showtimeId = createShowtime(movieId, hallId, "2030-01-15T18:00:00Z", "2030-01-15T20:49:00Z");
        seatA = seatId(hallId, 1, 1);
        seatB = seatId(hallId, 1, 2);
    }

    @DisplayName("Cancelling a booking refunds the payment and puts the seats back on sale")
    @Test
    void cancelRefundsAndFreesTheSeats() throws Exception
    {
        String bookingId = createBooking(showtimeId, seatA, seatB);

        cancel(customerToken, bookingId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledAt").isString())
                .andExpect(jsonPath("$.payment.status").value("REFUNDED"))
                // The tickets are still listed: a cancelled booking keeps its history.
                .andExpect(jsonPath("$.seats.length()").value(2));

        assertThat(paymentProvider.refundCount()).isEqualTo(1);
        anonymous.perform(get(SHOWTIMES + "/" + showtimeId + "/seats"))
                .andExpect(jsonPath("$.availableSeats").value(DEFAULT_SEAT_COUNT))
                .andExpect(jsonPath("$.rows[0].seats[0].status").value("AVAILABLE"));
    }

    @DisplayName("A seat from a cancelled booking can be sold again, and both bookings remain on record")
    @Test
    void cancelledSeatCanBeSoldAgain() throws Exception
    {
        String cancelled = createBooking(showtimeId, seatA);
        cancel(customerToken, cancelled).andExpect(status().isOk());

        String resold = createBookingAs(otherCustomerToken, showtimeId, seatA);

        mvc.perform(get(BOOKINGS + "/" + resold).header(HttpHeaders.AUTHORIZATION, bearer(otherCustomerToken)))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.seats[0].seatId").value(seatA));
        mvc.perform(get(BOOKINGS + "/" + cancelled).header(HttpHeaders.AUTHORIZATION, bearer(customerToken)))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.seats[0].seatId").value(seatA));
        // Two rows for the same seat and showtime, only one of them active.
        assertThat(jdbc.queryForList("SELECT active FROM booking_seats WHERE seat_id = ? ORDER BY id", Boolean.class, seatA))
                .containsExactly(false, true);
    }

    @DisplayName("Cancelling twice is harmless: the booking stays cancelled and is refunded once")
    @Test
    void cancellingTwiceRefundsOnce() throws Exception
    {
        String bookingId = createBooking(showtimeId, seatA);
        cancel(customerToken, bookingId).andExpect(status().isOk());

        cancel(customerToken, bookingId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.payment.status").value("REFUNDED"));

        assertThat(paymentProvider.refundCount()).isEqualTo(1);
    }

    @DisplayName("Five simultaneous cancellations of one booking refund it once")
    @Test
    void simultaneousCancellationsRefundOnce() throws Exception
    {
        String bookingId = createBooking(showtimeId, seatA);

        List<Integer> statuses = SimultaneousRequests.run(5,
                () -> cancel(customerToken, bookingId).andReturn().getResponse().getStatus());

        // Each request either cancelled it, found it already cancelled, or lost the race and was told to retry.
        assertThat(statuses).allMatch(status -> status == 200 || status == 409);
        assertThat(statuses).contains(200);
        assertThat(paymentProvider.refundCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM payments", String.class)).isEqualTo("REFUNDED");
        assertThat(jdbc.queryForObject("SELECT status FROM bookings", String.class)).isEqualTo("CANCELLED");
    }

    @DisplayName("Only the owner or an administrator can cancel a booking")
    @Test
    void onlyOwnerOrAdminCanCancel() throws Exception
    {
        String bookingId = createBooking(showtimeId, seatA);

        cancel(otherCustomerToken, bookingId).andExpect(problem(404, "BOOKING_NOT_FOUND"));
        anonymous.perform(post(BOOKINGS + "/" + bookingId + "/cancel")).andExpect(problem(401, "UNAUTHENTICATED"));
        assertThat(paymentProvider.refundCount()).isZero();

        cancel(adminToken, bookingId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @DisplayName("A booking cannot be cancelled once the showtime has started")
    @Test
    void cannotCancelAfterTheShowtimeStarted() throws Exception
    {
        String bookingId = createBooking(showtimeId, seatA);
        // Time passes: the showtime is now in the past.
        jdbc.update("UPDATE showtimes SET start_time = now() - interval '1 hour', end_time = now() + interval '1 hour' WHERE id = ?",
                showtimeId);

        cancel(customerToken, bookingId).andExpect(problem(409, "SHOWTIME_ALREADY_STARTED"));

        assertThat(paymentProvider.refundCount()).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM bookings", String.class)).isEqualTo("CONFIRMED");
    }

    @DisplayName("If the provider is down, the cancellation still goes through and the refund is completed later")
    @Test
    void refundIsRetriedWhenTheProviderIsDown() throws Exception
    {
        String bookingId = createBooking(showtimeId, seatA);
        paymentProvider.setRefundsFailing(true);

        // The customer is not made to wait for the provider: the booking is cancelled and the seat is free.
        cancel(customerToken, bookingId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.payment.status").value("REFUND_PENDING"));
        anonymous.perform(get(SHOWTIMES + "/" + showtimeId + "/seats"))
                .andExpect(jsonPath("$.rows[0].seats[0].status").value("AVAILABLE"));
        assertThat(paymentProvider.refundCount()).isZero();

        // Still down: the job tries and leaves the payment waiting.
        reconcileEverythingUnfinished();
        assertThat(jdbc.queryForObject("SELECT status FROM payments", String.class)).isEqualTo("REFUND_PENDING");

        // The provider recovers; the next run of the job sends the refund.
        paymentProvider.setRefundsFailing(false);
        reconcileEverythingUnfinished();

        assertThat(paymentProvider.refundCount()).isEqualTo(1);
        mvc.perform(get(BOOKINGS + "/" + bookingId).header(HttpHeaders.AUTHORIZATION, bearer(customerToken)))
                .andExpect(jsonPath("$.payment.status").value("REFUNDED"));
    }

    @DisplayName("Cancelling an unknown booking returns 404")
    @Test
    void cancelUnknownBooking() throws Exception
    {
        cancel(customerToken, "00000000-0000-0000-0000-00000000dead").andExpect(problem(404, "BOOKING_NOT_FOUND"));
    }

    private ResultActions cancel(String token, String bookingId) throws Exception
    {
        return mvc.perform(post(BOOKINGS + "/" + bookingId + "/cancel").header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    /** Runs the reconciliation job as if every unfinished payment were old enough to be picked up. */
    private void reconcileEverythingUnfinished()
    {
        Instant inTheFuture = Instant.now().plusSeconds(3600);
        reconciler.reconcile(inTheFuture, inTheFuture);
    }
}
