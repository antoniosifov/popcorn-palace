package com.antoniosifov.popcornpalace.checkout;

import com.antoniosifov.popcornpalace.payment.FakePaymentProvider;
import com.antoniosifov.popcornpalace.support.ApiIntegrationTest;
import com.antoniosifov.popcornpalace.support.SimultaneousRequests;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The failure every payment flow must rule out: a customer charged twice for
 * one purchase because two requests ran at the same time.
 */
class CheckoutConcurrencyTest extends ApiIntegrationTest
{
    private static final int SIMULTANEOUS_REQUESTS = 10;

    private long showtimeId;
    private String holdId;

    @BeforeEach
    void holdSeats() throws Exception
    {
        long movieId = createMovie("Interstellar");
        long hallId = createHall("Hall 1");
        showtimeId = createShowtime(movieId, hallId, "2030-01-15T18:00:00Z", "2030-01-15T20:49:00Z");
        holdId = createHold(customerToken, showtimeId, seatId(hallId, 1, 1), seatId(hallId, 1, 2));
    }

    @DisplayName("The same request sent ten times at once charges once; afterwards the same key replays the booking")
    @Test
    void sameKeyAtOnceChargesOnce() throws Exception
    {
        String key = "retry-storm-0001";

        // The slow token keeps the first request busy at the provider while the others arrive.
        List<Integer> statuses = SimultaneousRequests.run(SIMULTANEOUS_REQUESTS,
                () -> confirm(customerToken, showtimeId, holdId, key, FakePaymentProvider.TOKEN_SLOW)
                        .andReturn().getResponse().getStatus());

        assertThat(statuses).filteredOn(status -> status == 201).hasSize(1);
        assertThat(statuses).filteredOn(status -> status == 409).hasSize(SIMULTANEOUS_REQUESTS - 1);
        assertThat(paymentProvider.chargeCount()).isEqualTo(1);
        assertThat(count("payments")).isEqualTo(1);
        assertThat(count("bookings")).isEqualTo(1);

        // Once the first request has finished, a retry with the same key gets its result.
        confirm(customerToken, showtimeId, holdId, key, FakePaymentProvider.TOKEN_SLOW)
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"));
        assertThat(paymentProvider.chargeCount()).isEqualTo(1);
    }

    @DisplayName("Ten different purchase attempts for one hold at once still charge once")
    @Test
    void differentKeysAtOnceChargeOnce() throws Exception
    {
        List<Integer> statuses = SimultaneousRequests.run(SIMULTANEOUS_REQUESTS,
                () -> confirm(customerToken, showtimeId, holdId, UUID.randomUUID().toString(), FakePaymentProvider.TOKEN_SLOW)
                        .andReturn().getResponse().getStatus());

        assertThat(statuses).filteredOn(status -> status == 201).hasSize(1);
        assertThat(statuses).allMatch(status -> status == 201 || status == 409);
        // No second charge was ever made, so there is nothing to refund either.
        assertThat(paymentProvider.chargeCount()).isEqualTo(1);
        assertThat(paymentProvider.refundCount()).isZero();
        assertThat(count("bookings")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payments WHERE status = 'SUCCEEDED'", Long.class)).isEqualTo(1);
    }

    private long count(String table)
    {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }
}
