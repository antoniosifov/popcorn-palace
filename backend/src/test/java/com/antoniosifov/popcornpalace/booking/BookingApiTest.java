package com.antoniosifov.popcornpalace.booking;

import com.antoniosifov.popcornpalace.support.ApiIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
    private long showtimeId;

    @BeforeEach
    void createShowtime() throws Exception
    {
        long movieId = createMovie("Interstellar");
        showtimeId = createShowtime(movieId, "Hall 1", "2030-01-15T18:00:00Z", "2030-01-15T20:49:00Z");
    }

    @DisplayName("POST books a seat; the booking can then be fetched by id")
    @Test
    void booksSeat() throws Exception
    {
        postJson(BOOKINGS, bookingJson(showtimeId, 15, USER_ID))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/bookings/")))
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.showtimeId").value(showtimeId))
                .andExpect(jsonPath("$.seatNumber").value(15))
                .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.createdAt").isString());

        String created = postJson(BOOKINGS, bookingJson(showtimeId, 16, USER_ID))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String bookingId = JsonPath.read(created, "$.id");

        // Fetching the booking returns exactly what the create call returned.
        mvc.perform(get(BOOKINGS + "/{id}", bookingId))
                .andExpect(status().isOk())
                .andExpect(content().json(created, JsonCompareMode.STRICT));
    }

    @DisplayName("A seat cannot be booked twice for the same showtime")
    @Test
    void rejectsSeatAlreadyBooked() throws Exception
    {
        createBooking(showtimeId, 15);

        postJson(BOOKINGS, bookingJson(showtimeId, 15, UUID.randomUUID()))
                .andExpect(problem(409, "SEAT_ALREADY_BOOKED"))
                .andExpect(jsonPath("$.detail").value("Seat 15 is already booked for this showtime"));
    }

    @DisplayName("The same seat number is free in another showtime")
    @Test
    void allowsSameSeatInAnotherShowtime() throws Exception
    {
        long movieId = createMovie("Alien");
        long otherShowtimeId = createShowtime(movieId, "Hall 2", "2030-01-15T18:00:00Z", "2030-01-15T20:00:00Z");
        createBooking(showtimeId, 15);

        postJson(BOOKINGS, bookingJson(otherShowtimeId, 15, USER_ID))
                .andExpect(status().isCreated());
    }

    @DisplayName("The database itself rejects a duplicate seat, independent of the application check")
    @Test
    void databaseRejectsDuplicateSeat()
    {
        String insert = "INSERT INTO bookings (id, showtime_id, seat_number, user_id, created_at) VALUES (?, ?, 15, ?, now())";
        jdbc.update(insert, UUID.randomUUID(), showtimeId, USER_ID);

        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), showtimeId, USER_ID))
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("uq_bookings_showtime_seat");
    }

    @DisplayName("A booking for an unknown showtime is rejected")
    @Test
    void rejectsUnknownShowtime() throws Exception
    {
        postJson(BOOKINGS, bookingJson(999, 15, USER_ID))
                .andExpect(problem(404, "SHOWTIME_NOT_FOUND"));
    }

    @DisplayName("Seat numbers outside 1..300 are rejected")
    @Test
    void rejectsSeatOutOfRange() throws Exception
    {
        postJson(BOOKINGS, bookingJson(showtimeId, 0, USER_ID))
                .andExpect(problem(400, "VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("seatNumber"));

        postJson(BOOKINGS, bookingJson(showtimeId, 301, USER_ID))
                .andExpect(problem(400, "VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("seatNumber"));
    }

    @DisplayName("A booking without a user is rejected")
    @Test
    void rejectsMissingUser() throws Exception
    {
        postJson(BOOKINGS, "{\"showtimeId\": %d, \"seatNumber\": 15}".formatted(showtimeId))
                .andExpect(problem(400, "VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("userId"));
    }

    @DisplayName("A showtime that has already started can no longer be booked")
    @Test
    void rejectsBookingAfterShowtimeStarted() throws Exception
    {
        long movieId = createMovie("Alien");
        long pastShowtimeId = createShowtime(movieId, "Hall 2", "2020-01-15T18:00:00Z", "2020-01-15T20:00:00Z");

        postJson(BOOKINGS, bookingJson(pastShowtimeId, 15, USER_ID))
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
