package com.antoniosifov.popcornpalace.showtime;

import com.antoniosifov.popcornpalace.support.ApiIntegrationTest;
import com.antoniosifov.popcornpalace.support.SimultaneousRequests;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two managers scheduling the same hall at the same moment must not both
 * succeed. The application's overlap query cannot prevent that on its own;
 * the exclusion constraint ex_showtimes_hall_time does.
 */
class ShowtimeConcurrencyTest extends ApiIntegrationTest
{
    private static final int SIMULTANEOUS_REQUESTS = 20;

    @DisplayName("When many requests schedule the same hall and time at once, exactly one succeeds")
    @Test
    void onlyOneRequestGetsTheSlot() throws Exception
    {
        long movieId = createMovie("Interstellar");
        long hallId = createHall("Hall 1");
        String body = showtimeJson(movieId, hallId, "2030-01-15T18:00:00Z", "2030-01-15T20:49:00Z");

        List<String> results = SimultaneousRequests.run(SIMULTANEOUS_REQUESTS, () -> {
            var response = postJson(SHOWTIMES, body).andReturn().getResponse();
            return response.getStatus() + " " + response.getContentAsString();
        });

        assertThat(results).filteredOn(result -> result.startsWith("201 ")).hasSize(1);
        assertThat(results).filteredOn(result -> result.startsWith("409 ") && result.contains("SHOWTIME_OVERLAP"))
                .hasSize(SIMULTANEOUS_REQUESTS - 1);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM showtimes WHERE hall_id = ?", Long.class, hallId))
                .isEqualTo(1);
        // The losers' price rows were rolled back together with their showtimes.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM showtime_prices", Long.class)).isEqualTo(2);
    }
}
