package com.antoniosifov.popcornpalace.hold;

import com.antoniosifov.popcornpalace.support.ApiIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The checkout flow: hold seats, then confirm, release or let the hold expire. */
class HoldApiTest extends ApiIntegrationTest
{
    private static final String START = "2030-01-15T18:00:00Z";
    private static final String END = "2030-01-15T20:49:00Z";

    private long movieId;
    private long hallId;
    private long showtimeId;
    /** Row 1 seat 1, row 1 seat 2 (standard) and row 3 seat 1 (VIP). */
    private long seatA;
    private long seatB;
    private long vipSeat;

    @BeforeEach
    void createShowtime() throws Exception
    {
        movieId = createMovie("Interstellar");
        hallId = createHall("Hall 1");
        showtimeId = createShowtime(movieId, hallId, START, END);
        seatA = seatId(hallId, 1, 1);
        seatB = seatId(hallId, 1, 2);
        vipSeat = seatId(hallId, 3, 1);
    }

    // ------------------------------------------------------------ holding

    @DisplayName("Holding seats returns them with their prices, the total and an expiry time")
    @Test
    void holdsSeats() throws Exception
    {
        postJsonAs(customerToken, holdsUrl(showtimeId), holdJson(vipSeat, seatA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.showtimeId").value(showtimeId))
                // Seats come back in hall order, whatever order they were requested in.
                .andExpect(jsonPath("$.seats.length()").value(2))
                .andExpect(jsonPath("$.seats[0].seatId").value(seatA))
                .andExpect(jsonPath("$.seats[0].row").value(1))
                .andExpect(jsonPath("$.seats[0].seatNumber").value(1))
                .andExpect(jsonPath("$.seats[0].category").value("STANDARD"))
                .andExpect(jsonPath("$.seats[0].price").value(14.5))
                .andExpect(jsonPath("$.seats[1].seatId").value(vipSeat))
                .andExpect(jsonPath("$.seats[1].category").value("VIP"))
                .andExpect(jsonPath("$.seats[1].price").value(22.0))
                .andExpect(jsonPath("$.totalPrice").value(36.5))
                .andExpect(jsonPath("$.expiresAt").isString());
    }

    @DisplayName("A hold lives for ten minutes in Redis and then disappears by itself")
    @Test
    void holdHasTimeToLive() throws Exception
    {
        createHold(customerToken, showtimeId, seatA);

        Long secondsLeft = redis.getExpire("hold:{%d}:seat:%d".formatted(showtimeId, seatA), TimeUnit.SECONDS);
        assertThat(secondsLeft).isBetween(Duration.ofMinutes(9).toSeconds(), Duration.ofMinutes(10).toSeconds());
    }

    @DisplayName("A held seat shows as HELD to everyone, and as their own to the holder")
    @Test
    void heldSeatsShowOnTheSeatMap() throws Exception
    {
        createHold(customerToken, showtimeId, seatA);

        anonymous.perform(get(SHOWTIMES + "/" + showtimeId + "/seats"))
                .andExpect(jsonPath("$.availableSeats").value(DEFAULT_SEAT_COUNT - 1))
                .andExpect(jsonPath("$.rows[0].seats[0].status").value("HELD"))
                .andExpect(jsonPath("$.rows[0].seats[0].heldByMe").value(false))
                .andExpect(jsonPath("$.rows[0].seats[1].status").value("AVAILABLE"));

        seatMapAs(customerToken)
                .andExpect(jsonPath("$.rows[0].seats[0].status").value("HELD"))
                .andExpect(jsonPath("$.rows[0].seats[0].heldByMe").value(true));

        seatMapAs(otherCustomerToken)
                .andExpect(jsonPath("$.rows[0].seats[0].status").value("HELD"))
                .andExpect(jsonPath("$.rows[0].seats[0].heldByMe").value(false));
    }

    @DisplayName("A seat held by one customer cannot be held by another; the error names the seat")
    @Test
    void anotherCustomerCannotHoldAHeldSeat() throws Exception
    {
        createHold(customerToken, showtimeId, seatA);

        postJsonAs(otherCustomerToken, holdsUrl(showtimeId), holdJson(seatA))
                .andExpect(problem(409, "SEAT_ALREADY_HELD"))
                .andExpect(jsonPath("$.seatIds.length()").value(1))
                .andExpect(jsonPath("$.seatIds[0]").value(seatA));
    }

    @DisplayName("A hold is all or nothing: if one seat is taken, none of the others is held")
    @Test
    void holdIsAllOrNothing() throws Exception
    {
        createHold(customerToken, showtimeId, seatA);

        postJsonAs(otherCustomerToken, holdsUrl(showtimeId), holdJson(seatB, seatA, vipSeat))
                .andExpect(problem(409, "SEAT_ALREADY_HELD"))
                .andExpect(jsonPath("$.seatIds.length()").value(1))
                .andExpect(jsonPath("$.seatIds[0]").value(seatA));

        // The free seats of the failed request were not taken.
        seatMapAs(otherCustomerToken)
                .andExpect(jsonPath("$.availableSeats").value(DEFAULT_SEAT_COUNT - 1))
                .andExpect(jsonPath("$.rows[0].seats[1].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.rows[2].seats[0].status").value("AVAILABLE"));
        mvc.perform(get(holdsUrl(showtimeId) + "/mine").header(HttpHeaders.AUTHORIZATION, bearer(otherCustomerToken)))
                .andExpect(problem(404, "HOLD_NOT_FOUND"));
    }

    @DisplayName("A customer has one hold per showtime, but may hold seats in several showtimes")
    @Test
    void oneHoldPerCustomerPerShowtime() throws Exception
    {
        String holdId = createHold(customerToken, showtimeId, seatA);

        postJsonAs(customerToken, holdsUrl(showtimeId), holdJson(seatB))
                .andExpect(problem(409, "HOLD_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.holdId").value(holdId));

        long laterShowtimeId = createShowtime(movieId, hallId, "2030-01-15T21:00:00Z", "2030-01-15T23:00:00Z");
        postJsonAs(customerToken, holdsUrl(laterShowtimeId), holdJson(seatA))
                .andExpect(status().isCreated());
    }

    @DisplayName("A customer can look up their current hold; nobody else can")
    @Test
    void returnsOwnHold() throws Exception
    {
        String holdId = createHold(customerToken, showtimeId, seatA, seatB);

        mvc.perform(get(holdsUrl(showtimeId) + "/mine").header(HttpHeaders.AUTHORIZATION, bearer(customerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(holdId))
                .andExpect(jsonPath("$.seats.length()").value(2))
                .andExpect(jsonPath("$.totalPrice").value(29.0));

        mvc.perform(get(holdsUrl(showtimeId) + "/mine").header(HttpHeaders.AUTHORIZATION, bearer(otherCustomerToken)))
                .andExpect(problem(404, "HOLD_NOT_FOUND"));
    }

    // ------------------------------------------------------------ releasing and expiring

    @DisplayName("Releasing a hold frees its seats at once; releasing again is harmless")
    @Test
    void releaseFreesTheSeats() throws Exception
    {
        String holdId = createHold(customerToken, showtimeId, seatA);

        release(customerToken, holdId).andExpect(status().isNoContent());

        seatMapAs(customerToken).andExpect(jsonPath("$.availableSeats").value(DEFAULT_SEAT_COUNT));
        postJsonAs(otherCustomerToken, holdsUrl(showtimeId), holdJson(seatA)).andExpect(status().isCreated());

        release(customerToken, holdId).andExpect(status().isNoContent());
        // The second release did not touch the seat, which now belongs to the other customer.
        seatMapAs(otherCustomerToken).andExpect(jsonPath("$.rows[0].seats[0].heldByMe").value(true));
    }

    @DisplayName("A customer cannot release someone else's hold")
    @Test
    void cannotReleaseSomeoneElsesHold() throws Exception
    {
        String holdId = createHold(customerToken, showtimeId, seatA);

        release(otherCustomerToken, holdId).andExpect(problem(404, "HOLD_NOT_FOUND"));

        seatMapAs(customerToken).andExpect(jsonPath("$.rows[0].seats[0].heldByMe").value(true));
    }

    @DisplayName("When a hold runs out, its seats are free again and it can no longer be confirmed")
    @Test
    void expiredHoldFreesSeatsAndCannotBeConfirmed() throws Exception
    {
        String holdId = createHold(customerToken, showtimeId, seatA);

        expireHolds();

        seatMapAs(customerToken).andExpect(jsonPath("$.availableSeats").value(DEFAULT_SEAT_COUNT));
        confirm(customerToken, showtimeId, holdId).andExpect(problem(404, "HOLD_NOT_FOUND"));
        assertThat(countBookings()).isZero();

        // Someone else can take the seat, and the first customer can start over.
        postJsonAs(otherCustomerToken, holdsUrl(showtimeId), holdJson(seatA)).andExpect(status().isCreated());
        postJsonAs(customerToken, holdsUrl(showtimeId), holdJson(seatB)).andExpect(status().isCreated());
    }

    // ------------------------------------------------------------ confirming

    @DisplayName("Confirming a hold books its seats and ends the hold")
    @Test
    void confirmTurnsHoldIntoBooking() throws Exception
    {
        String holdId = createHold(customerToken, showtimeId, seatA, vipSeat);

        confirm(customerToken, showtimeId, holdId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.seats.length()").value(2))
                .andExpect(jsonPath("$.totalPrice").value(36.5))
                .andExpect(jsonPath("$.userId").value(CUSTOMER_ID.toString()));

        seatMapAs(customerToken)
                .andExpect(jsonPath("$.availableSeats").value(DEFAULT_SEAT_COUNT - 2))
                .andExpect(jsonPath("$.rows[0].seats[0].status").value("BOOKED"))
                .andExpect(jsonPath("$.rows[0].seats[0].heldByMe").value(false))
                .andExpect(jsonPath("$.rows[2].seats[0].status").value("BOOKED"));
        mvc.perform(get(holdsUrl(showtimeId) + "/mine").header(HttpHeaders.AUTHORIZATION, bearer(customerToken)))
                .andExpect(problem(404, "HOLD_NOT_FOUND"));
        // No hold keys are left behind for this showtime.
        assertThat(redis.keys("hold:*")).isEmpty();
    }

    @DisplayName("A hold can be confirmed only once")
    @Test
    void cannotConfirmTwice() throws Exception
    {
        String holdId = createHold(customerToken, showtimeId, seatA);
        confirmHold(customerToken, showtimeId, holdId);

        confirm(customerToken, showtimeId, holdId).andExpect(problem(404, "HOLD_NOT_FOUND"));

        assertThat(countBookings()).isEqualTo(1);
    }

    @DisplayName("A customer cannot confirm someone else's hold")
    @Test
    void cannotConfirmSomeoneElsesHold() throws Exception
    {
        String holdId = createHold(customerToken, showtimeId, seatA);

        confirm(otherCustomerToken, showtimeId, holdId).andExpect(problem(404, "HOLD_NOT_FOUND"));

        assertThat(countBookings()).isZero();
        seatMapAs(customerToken).andExpect(jsonPath("$.rows[0].seats[0].heldByMe").value(true));
    }

    @DisplayName("The price shown when the seats were held is the price charged, even if prices change before confirming")
    @Test
    void confirmChargesThePriceThatWasHeld() throws Exception
    {
        String holdId = createHold(customerToken, showtimeId, seatA);

        putJson(SHOWTIMES + "/" + showtimeId, showtimeJson(movieId, hallId, START, END, """
                {"STANDARD": 19.90, "VIP": 30.00}"""))
                .andExpect(status().isOk());

        confirm(customerToken, showtimeId, holdId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.seats[0].price").value(14.5))
                .andExpect(jsonPath("$.totalPrice").value(14.5));
    }

    @DisplayName("A booked seat cannot be held; the error names it and nothing else is held")
    @Test
    void cannotHoldABookedSeat() throws Exception
    {
        createBooking(showtimeId, seatA);

        postJsonAs(otherCustomerToken, holdsUrl(showtimeId), holdJson(seatA, seatB))
                .andExpect(problem(409, "SEAT_ALREADY_BOOKED"))
                .andExpect(jsonPath("$.seatIds.length()").value(1))
                .andExpect(jsonPath("$.seatIds[0]").value(seatA));

        seatMapAs(otherCustomerToken).andExpect(jsonPath("$.rows[0].seats[1].status").value("AVAILABLE"));
        assertThat(redis.keys("hold:*")).isEmpty();
    }

    // ------------------------------------------------------------ invalid requests

    @DisplayName("A hold needs at least one seat, each seat once, and at most eight seats")
    @Test
    void rejectsInvalidSeatLists() throws Exception
    {
        postJsonAs(customerToken, holdsUrl(showtimeId), "{\"seatIds\": []}")
                .andExpect(problem(400, "VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("seatIds"));

        postJsonAs(customerToken, holdsUrl(showtimeId), holdJson(seatA, seatA))
                .andExpect(problem(400, "VALIDATION_FAILED"));

        List<Long> nineSeats = jdbc.queryForList("SELECT id FROM seats WHERE hall_id = ? ORDER BY id LIMIT 9", Long.class, hallId);
        postJsonAs(customerToken, holdsUrl(showtimeId), holdJson(nineSeats.stream().mapToLong(Long::longValue).toArray()))
                .andExpect(problem(400, "TOO_MANY_SEATS"));

        assertThat(redis.keys("hold:*")).isEmpty();
    }

    @DisplayName("Seats that do not exist or belong to another hall are rejected and named")
    @Test
    void rejectsUnknownAndForeignSeats() throws Exception
    {
        long otherHallId = createHall("Hall 2");
        long foreignSeat = seatId(otherHallId, 1, 1);

        postJsonAs(customerToken, holdsUrl(showtimeId), holdJson(seatA, 99999))
                .andExpect(problem(404, "SEAT_NOT_FOUND"))
                .andExpect(jsonPath("$.seatIds[0]").value(99999));

        postJsonAs(customerToken, holdsUrl(showtimeId), holdJson(seatA, foreignSeat))
                .andExpect(problem(400, "SEAT_NOT_IN_HALL"))
                .andExpect(jsonPath("$.seatIds[0]").value(foreignSeat));

        assertThat(redis.keys("hold:*")).isEmpty();
    }

    @DisplayName("Seats cannot be held for an unknown showtime or one that has already started")
    @Test
    void rejectsUnknownOrStartedShowtime() throws Exception
    {
        postJsonAs(customerToken, holdsUrl(999), holdJson(seatA))
                .andExpect(problem(404, "SHOWTIME_NOT_FOUND"));

        long pastShowtimeId = createShowtime(movieId, hallId, "2020-01-15T18:00:00Z", "2020-01-15T20:00:00Z");
        postJsonAs(customerToken, holdsUrl(pastShowtimeId), holdJson(seatA))
                .andExpect(problem(409, "SHOWTIME_ALREADY_STARTED"));
    }

    @DisplayName("Holding seats needs a login, but not the administrator role")
    @Test
    void requiresLoginButNotAdmin() throws Exception
    {
        anonymous.perform(post(holdsUrl(showtimeId)).contentType(MediaType.APPLICATION_JSON).content(holdJson(seatA)))
                .andExpect(problem(401, "UNAUTHENTICATED"));
        anonymous.perform(get(holdsUrl(showtimeId) + "/mine"))
                .andExpect(problem(401, "UNAUTHENTICATED"));

        // The path is under /showtimes, whose write operations are otherwise for administrators only.
        postJsonAs(customerToken, holdsUrl(showtimeId), holdJson(seatA)).andExpect(status().isCreated());
    }

    // ------------------------------------------------------------ helpers

    private ResultActions seatMapAs(String token) throws Exception
    {
        return mvc.perform(get(SHOWTIMES + "/" + showtimeId + "/seats").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
    }

    private ResultActions release(String token, String holdId) throws Exception
    {
        return mvc.perform(delete(holdsUrl(showtimeId) + "/" + holdId).header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private long countBookings()
    {
        return jdbc.queryForObject("SELECT count(*) FROM bookings", Long.class);
    }
}
