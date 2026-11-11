package com.antoniosifov.popcornpalace.hold;

import com.antoniosifov.popcornpalace.support.ApiIntegrationTest;
import com.antoniosifov.popcornpalace.support.SimultaneousRequests;
import com.antoniosifov.popcornpalace.user.Role;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a ticket system has to survive: a popular showtime opens and many
 * customers go for the same seats in the same instant.
 */
class HoldConcurrencyTest extends ApiIntegrationTest
{
    private static final int CUSTOMERS = 50;

    private long showtimeId;
    private long hallId;
    private final List<String> customerTokens = new ArrayList<>();

    @BeforeEach
    void createShowtimeAndCustomers() throws Exception
    {
        long movieId = createMovie("Interstellar");
        hallId = createHall("Hall 1", """
                [{"rows": 1, "seatsPerRow": 6, "category": "STANDARD"}]""");
        showtimeId = createShowtime(movieId, hallId, "2030-01-15T18:00:00Z", "2030-01-15T20:49:00Z");

        customerTokens.clear();
        for (int i = 0; i < CUSTOMERS; i++) {
            UUID id = UUID.randomUUID();
            insertUser(id, "racer" + i + "@test.local", "Racer " + i, Role.CUSTOMER);
            customerTokens.add(tokenService.issue(id, Role.CUSTOMER, 0).value());
        }
    }

    @DisplayName("50 customers grab the same two seats at once: exactly one of them gets both")
    @Test
    void oneCustomerWinsBothSeats() throws Exception
    {
        long seatA = seatId(hallId, 1, 1);
        long seatB = seatId(hallId, 1, 2);
        AtomicInteger next = new AtomicInteger();

        List<String> results = SimultaneousRequests.run(CUSTOMERS, () -> {
            String token = customerTokens.get(next.getAndIncrement());
            var response = postJsonAs(token, holdsUrl(showtimeId), holdJson(seatA, seatB)).andReturn().getResponse();
            return response.getStatus() + " " + response.getContentAsString();
        });

        List<String> winners = results.stream().filter(result -> result.startsWith("201 ")).toList();
        assertThat(winners).hasSize(1);
        assertThat(results).filteredOn(result -> result.startsWith("409 ") && result.contains("SEAT_ALREADY_HELD"))
                .hasSize(CUSTOMERS - 1);

        // Both seats belong to the winner's hold, and no loser left anything behind.
        String winningHoldId = JsonPath.read(winners.get(0).substring(4), "$.id");
        assertThat(redis.opsForValue().get(seatKey(seatA))).isEqualTo(winningHoldId);
        assertThat(redis.opsForValue().get(seatKey(seatB))).isEqualTo(winningHoldId);
        assertThat(redis.keys("hold:*:user:*")).hasSize(1);
        assertThat(redis.keys("hold:*:rec:*")).hasSize(1);
    }

    @DisplayName("Overlapping selections never leave a customer with half of what they asked for")
    @Test
    void overlappingPairsAreNeverSplit() throws Exception
    {
        // Six seats in a row; customer i asks for the pair starting at seat (i mod 5) + 1,
        // so neighbouring requests overlap by one seat.
        List<Long> seats = jdbc.queryForList("SELECT id FROM seats WHERE hall_id = ? ORDER BY seat_number", Long.class, hallId);
        AtomicInteger next = new AtomicInteger();

        List<String> results = SimultaneousRequests.run(CUSTOMERS, () -> {
            int customer = next.getAndIncrement();
            int first = customer % 5;
            var response = postJsonAs(customerTokens.get(customer), holdsUrl(showtimeId),
                    holdJson(seats.get(first), seats.get(first + 1))).andReturn().getResponse();
            return response.getStatus() + " " + response.getContentAsString();
        });

        List<String> winners = results.stream().filter(result -> result.startsWith("201 ")).toList();
        // Six seats fit at most three non-overlapping pairs.
        assertThat(winners).hasSizeBetween(1, 3);
        assertThat(results).allMatch(result -> result.startsWith("201 ") || result.startsWith("409 "));

        // Every winner holds exactly the two seats they asked for; nobody holds a single seat.
        long heldSeats = seats.stream().filter(seat -> redis.opsForValue().get(seatKey(seat)) != null).count();
        assertThat(heldSeats).isEqualTo(2L * winners.size());
        for (String winner : winners) {
            String holdId = JsonPath.read(winner.substring(4), "$.id");
            List<Integer> heldIds = JsonPath.read(winner.substring(4), "$.seats[*].seatId");
            assertThat(heldIds).hasSize(2);
            for (Integer seatId : heldIds) {
                assertThat(redis.opsForValue().get(seatKey(seatId))).isEqualTo(holdId);
            }
        }
    }

    @DisplayName("Ten simultaneous payments for one hold (an impatient double click) charge once and create one booking")
    @Test
    void confirmingTheSameHoldManyTimesBooksOnce() throws Exception
    {
        String token = customerTokens.get(0);
        String holdId = createHold(token, showtimeId, seatId(hallId, 1, 1), seatId(hallId, 1, 2));

        List<Integer> statuses = SimultaneousRequests.run(10,
                () -> confirm(token, showtimeId, holdId).andReturn().getResponse().getStatus());

        assertThat(statuses).filteredOn(status -> status == 201).hasSize(1);
        // The others were told a payment is already in progress, or found the hold already gone.
        assertThat(statuses).allMatch(status -> status == 201 || status == 404 || status == 409);
        // And, most importantly, the customer was charged once.
        assertThat(paymentProvider.chargeCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bookings", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM booking_seats", Long.class)).isEqualTo(2);
    }

    private String seatKey(long seatId)
    {
        return "hold:{%d}:seat:%d".formatted(showtimeId, seatId);
    }
}
