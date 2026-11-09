package com.antoniosifov.popcornpalace.booking;

import com.antoniosifov.popcornpalace.support.ApiIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.json.JsonCompareMode;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BookingApiTest extends ApiIntegrationTest
{
    private static final String START = "2030-01-15T18:00:00Z";
    private static final String END = "2030-01-15T20:49:00Z";
    private static final String INSERT_BOOKING = """
            INSERT INTO bookings (id, showtime_id, hall_id, seat_id, user_id, price, created_at)
            VALUES (?, ?, ?, ?, ?, 14.50, now())""";

    private long movieId;
    private long hallId;
    private long showtimeId;

    @BeforeEach
    void createShowtime() throws Exception
    {
        movieId = createMovie("Interstellar");
        hallId = createHall("Hall 1");
        showtimeId = createShowtime(movieId, hallId, START, END);
    }

    @DisplayName("POST books a seat and returns where it is and what it costs")
    @Test
    void booksSeat() throws Exception
    {
        long seatId = seatId(hallId, 2, 3);

        String created = postJson(BOOKINGS, bookingJson(showtimeId, seatId, USER_ID))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/bookings/")))
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.showtimeId").value(showtimeId))
                .andExpect(jsonPath("$.seatId").value(seatId))
                .andExpect(jsonPath("$.row").value(2))
                .andExpect(jsonPath("$.seatNumber").value(3))
                .andExpect(jsonPath("$.category").value("STANDARD"))
                .andExpect(jsonPath("$.price").value(14.5))
                .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.createdAt").isString())
                .andReturn().getResponse().getContentAsString();

        // Fetching the booking returns exactly what the create call returned.
        String bookingId = JsonPath.read(created, "$.id");
        mvc.perform(get(BOOKINGS + "/{id}", bookingId))
                .andExpect(status().isOk())
                .andExpect(content().json(created, JsonCompareMode.STRICT));
    }

    @DisplayName("A VIP seat is sold at the showtime's VIP price")
    @Test
    void chargesThePriceOfTheSeatCategory() throws Exception
    {
        postJson(BOOKINGS, bookingJson(showtimeId, seatId(hallId, 3, 1), USER_ID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.category").value("VIP"))
                .andExpect(jsonPath("$.price").value(22.0));
    }

    @DisplayName("A booking keeps the price it was sold for when the showtime's prices change later")
    @Test
    void keepsThePricePaid() throws Exception
    {
        String bookingId = createBooking(showtimeId, seatId(hallId, 1, 1));

        putJson(SHOWTIMES + "/" + showtimeId, showtimeJson(movieId, hallId, START, END, """
                {"STANDARD": 19.90, "VIP": 30.00}"""))
                .andExpect(status().isOk());

        mvc.perform(get(BOOKINGS + "/{id}", bookingId))
                .andExpect(jsonPath("$.price").value(14.5));

        postJson(BOOKINGS, bookingJson(showtimeId, seatId(hallId, 1, 2), USER_ID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.price").value(19.9));
    }

    @DisplayName("A seat cannot be booked twice for the same showtime")
    @Test
    void rejectsSeatAlreadyBooked() throws Exception
    {
        long seatId = seatId(hallId, 1, 2);
        createBooking(showtimeId, seatId);

        postJson(BOOKINGS, bookingJson(showtimeId, seatId, UUID.randomUUID()))
                .andExpect(problem(409, "SEAT_ALREADY_BOOKED"))
                .andExpect(jsonPath("$.detail").value("Row 1 seat 2 is already booked for this showtime"));
    }

    @DisplayName("The same seat is free in another showtime of the same hall")
    @Test
    void allowsSameSeatInAnotherShowtime() throws Exception
    {
        long laterShowtimeId = createShowtime(movieId, hallId, "2030-01-15T21:00:00Z", "2030-01-15T23:00:00Z");
        long seatId = seatId(hallId, 1, 2);
        createBooking(showtimeId, seatId);

        postJson(BOOKINGS, bookingJson(laterShowtimeId, seatId, USER_ID))
                .andExpect(status().isCreated());
    }

    @DisplayName("A seat from another hall cannot be booked for this showtime")
    @Test
    void rejectsSeatFromAnotherHall() throws Exception
    {
        long otherHallId = createHall("Hall 2");

        postJson(BOOKINGS, bookingJson(showtimeId, seatId(otherHallId, 1, 1), USER_ID))
                .andExpect(problem(400, "SEAT_NOT_IN_HALL"));
    }

    @DisplayName("The database itself rejects a duplicate seat, independent of the application check")
    @Test
    void databaseRejectsDuplicateSeat()
    {
        long seatId = seatId(hallId, 1, 2);
        jdbc.update(INSERT_BOOKING, UUID.randomUUID(), showtimeId, hallId, seatId, USER_ID);

        assertThatThrownBy(() -> jdbc.update(INSERT_BOOKING, UUID.randomUUID(), showtimeId, hallId, seatId, USER_ID))
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("uq_bookings_showtime_seat");
    }

    @DisplayName("The database itself rejects a seat that is not in the showtime's hall")
    @Test
    void databaseRejectsSeatFromAnotherHall() throws Exception
    {
        long otherHallId = createHall("Hall 2");
        long foreignSeatId = seatId(otherHallId, 1, 1);

        // Claiming the seat is in the showtime's hall: the seat's own hall disagrees.
        assertThatThrownBy(() -> jdbc.update(INSERT_BOOKING, UUID.randomUUID(), showtimeId, hallId, foreignSeatId, USER_ID))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_bookings_seat");

        // Claiming the showtime runs in the seat's hall: the showtime's own hall disagrees.
        assertThatThrownBy(() -> jdbc.update(INSERT_BOOKING, UUID.randomUUID(), showtimeId, otherHallId, foreignSeatId, USER_ID))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_bookings_showtime");
    }

    @DisplayName("A booking for an unknown showtime or seat is rejected")
    @Test
    void rejectsUnknownShowtimeOrSeat() throws Exception
    {
        postJson(BOOKINGS, bookingJson(999, seatId(hallId, 1, 1), USER_ID))
                .andExpect(problem(404, "SHOWTIME_NOT_FOUND"));

        postJson(BOOKINGS, bookingJson(showtimeId, 99999, USER_ID))
                .andExpect(problem(404, "SEAT_NOT_FOUND"));
    }

    @DisplayName("A booking without a user or seat is rejected")
    @Test
    void rejectsMissingFields() throws Exception
    {
        postJson(BOOKINGS, "{\"showtimeId\": %d}".formatted(showtimeId))
                .andExpect(problem(400, "VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("seatId"))
                .andExpect(jsonPath("$.errors[1].field").value("userId"));
    }

    @DisplayName("A showtime that has already started can no longer be booked")
    @Test
    void rejectsBookingAfterShowtimeStarted() throws Exception
    {
        long pastShowtimeId = createShowtime(movieId, hallId, "2020-01-15T18:00:00Z", "2020-01-15T20:00:00Z");

        postJson(BOOKINGS, bookingJson(pastShowtimeId, seatId(hallId, 1, 1), USER_ID))
                .andExpect(problem(409, "SHOWTIME_ALREADY_STARTED"));
    }

    @DisplayName("GET of an unknown booking returns 404; a malformed id returns 400")
    @Test
    void getUnknownBooking() throws Exception
    {
        mvc.perform(get(BOOKINGS + "/{id}", UUID.randomUUID()))
                .andExpect(problem(404, "BOOKING_NOT_FOUND"));

        mvc.perform(get(BOOKINGS + "/not-a-uuid"))
                .andExpect(problem(400, "BAD_REQUEST"));
    }
}
