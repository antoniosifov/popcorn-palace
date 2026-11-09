package com.antoniosifov.popcornpalace.booking;

import com.antoniosifov.popcornpalace.support.ApiIntegrationTest;
import com.antoniosifov.popcornpalace.support.SimultaneousRequests;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The scenario a booking system must get right: many people click the same
 * seat at the same moment. Exactly one of them may get it.
 */
class BookingConcurrencyTest extends ApiIntegrationTest
{
    private static final int SIMULTANEOUS_REQUESTS = 30;

    @DisplayName("When many requests book the same seat at once, exactly one succeeds")
    @Test
    void onlyOneRequestWinsTheSeat() throws Exception
    {
        long movieId = createMovie("Interstellar");
        long hallId = createHall("Hall 1");
        long showtimeId = createShowtime(movieId, hallId, "2030-01-15T18:00:00Z", "2030-01-15T20:49:00Z");
        long seatId = seatId(hallId, 1, 3);

        List<String> results = SimultaneousRequests.run(SIMULTANEOUS_REQUESTS, () -> {
            var response = postJsonAs(customerToken, BOOKINGS, bookingJson(showtimeId, seatId))
                    .andReturn().getResponse();
            return response.getStatus() + " " + response.getContentAsString();
        });

        assertThat(results).filteredOn(result -> result.startsWith("201 ")).hasSize(1);
        assertThat(results).filteredOn(result -> result.startsWith("409 ") && result.contains("SEAT_ALREADY_BOOKED"))
                .hasSize(SIMULTANEOUS_REQUESTS - 1);

        Long rowsForSeat = jdbc.queryForObject(
                "SELECT count(*) FROM bookings WHERE showtime_id = ? AND seat_id = ?", Long.class, showtimeId, seatId);
        assertThat(rowsForSeat).isEqualTo(1);
    }
}
