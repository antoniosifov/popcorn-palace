package com.antoniosifov.popcornpalace.booking;

import com.antoniosifov.popcornpalace.support.ApiIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The scenario a booking system must get right: many people click the same
 * seat at the same moment. Exactly one of them may get it.
 */
class BookingConcurrencyTest extends ApiIntegrationTest
{
    private static final int SIMULTANEOUS_REQUESTS = 30;
    private static final int SEAT = 7;

    @DisplayName("When many requests book the same seat at once, exactly one succeeds")
    @Test
    void onlyOneRequestWinsTheSeat() throws Exception
    {
        long movieId = createMovie("Interstellar");
        long showtimeId = createShowtime(movieId, "Hall 1", "2030-01-15T18:00:00Z", "2030-01-15T20:49:00Z");

        ExecutorService executor = Executors.newFixedThreadPool(SIMULTANEOUS_REQUESTS);
        CountDownLatch allReady = new CountDownLatch(SIMULTANEOUS_REQUESTS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> responses = new ArrayList<>();

        try {
            for (int i = 0; i < SIMULTANEOUS_REQUESTS; i++) {
                responses.add(executor.submit(() -> {
                    allReady.countDown();
                    start.await(); // every thread waits here, then all fire together
                    var response = postJson(BOOKINGS, bookingJson(showtimeId, SEAT, UUID.randomUUID()))
                            .andReturn().getResponse();
                    return response.getStatus() + " " + response.getContentAsString();
                }));
            }
            assertThat(allReady.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<String> results = new ArrayList<>();
            for (Future<String> response : responses) {
                results.add(response.get(30, TimeUnit.SECONDS));
            }

            assertThat(results).filteredOn(result -> result.startsWith("201 ")).hasSize(1);
            assertThat(results).filteredOn(result -> result.startsWith("409 ") && result.contains("SEAT_ALREADY_BOOKED"))
                    .hasSize(SIMULTANEOUS_REQUESTS - 1);
        }
        finally {
            executor.shutdownNow();
        }

        Long rowsForSeat = jdbc.queryForObject(
                "SELECT count(*) FROM bookings WHERE showtime_id = ? AND seat_number = ?", Long.class, showtimeId, SEAT);
        assertThat(rowsForSeat).isEqualTo(1);
    }
}
