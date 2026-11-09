package com.antoniosifov.popcornpalace.booking;

import com.antoniosifov.popcornpalace.support.ApiIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bookings as the result of a confirmed hold, and the database rules that
 * protect them no matter how a row gets written.
 */
class BookingApiTest extends ApiIntegrationTest
{
    private static final String START = "2030-01-15T18:00:00Z";
    private static final String END = "2030-01-15T20:49:00Z";

    private static final String INSERT_BOOKING = """
            INSERT INTO bookings (id, showtime_id, hall_id, user_id, total_price, created_at)
            VALUES (?, ?, ?, ?, 14.50, now())""";
    private static final String INSERT_BOOKING_SEAT = """
            INSERT INTO booking_seats (booking_id, showtime_id, hall_id, seat_id, price)
            VALUES (?, ?, ?, ?, 14.50)""";

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

    @DisplayName("A confirmed hold becomes a booking with one ticket per seat and the total")
    @Test
    void confirmedHoldBecomesBookingWithTickets() throws Exception
    {
        long vipSeat = seatId(hallId, 3, 1);
        long standardSeat = seatId(hallId, 2, 3);
        String holdId = createHold(customerToken, showtimeId, vipSeat, standardSeat);

        MvcResult result = confirm(customerToken, showtimeId, holdId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.showtimeId").value(showtimeId))
                .andExpect(jsonPath("$.movieTitle").value("Interstellar"))
                .andExpect(jsonPath("$.hallName").value("Hall 1"))
                .andExpect(jsonPath("$.startTime").value(START))
                // Tickets are listed in hall order, whatever order the seats were picked in.
                .andExpect(jsonPath("$.seats.length()").value(2))
                .andExpect(jsonPath("$.seats[0].seatId").value(standardSeat))
                .andExpect(jsonPath("$.seats[0].row").value(2))
                .andExpect(jsonPath("$.seats[0].seatNumber").value(3))
                .andExpect(jsonPath("$.seats[0].category").value("STANDARD"))
                .andExpect(jsonPath("$.seats[0].price").value(14.5))
                .andExpect(jsonPath("$.seats[1].seatId").value(vipSeat))
                .andExpect(jsonPath("$.seats[1].category").value("VIP"))
                .andExpect(jsonPath("$.seats[1].price").value(22.0))
                .andExpect(jsonPath("$.totalPrice").value(36.5))
                .andExpect(jsonPath("$.userId").value(CUSTOMER_ID.toString()))
                .andExpect(jsonPath("$.createdAt").isString())
                .andReturn();

        // The Location header points at the booking, and fetching it returns exactly the same body.
        String created = result.getResponse().getContentAsString();
        String bookingId = JsonPath.read(created, "$.id");
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).endsWith("/api/v1/bookings/" + bookingId);
        mvc.perform(get(BOOKINGS + "/{id}", bookingId).header(HttpHeaders.AUTHORIZATION, bearer(customerToken)))
                .andExpect(status().isOk())
                .andExpect(content().json(created, JsonCompareMode.STRICT));
    }

    @DisplayName("A booking keeps the prices it was sold for when the showtime's prices change later")
    @Test
    void keepsThePricePaid() throws Exception
    {
        String bookingId = createBooking(showtimeId, seatId(hallId, 1, 1));

        putJson(SHOWTIMES + "/" + showtimeId, showtimeJson(movieId, hallId, START, END, """
                {"STANDARD": 19.90, "VIP": 30.00}"""))
                .andExpect(status().isOk());

        mvc.perform(get(BOOKINGS + "/{id}", bookingId).header(HttpHeaders.AUTHORIZATION, bearer(customerToken)))
                .andExpect(jsonPath("$.seats[0].price").value(14.5))
                .andExpect(jsonPath("$.totalPrice").value(14.5));
    }

    @DisplayName("Bookings cannot be created directly, only by confirming a hold")
    @Test
    void bookingsAreNotCreatedDirectly() throws Exception
    {
        postJsonAs(customerToken, BOOKINGS, "{\"showtimeId\": %d, \"seatId\": %d}".formatted(showtimeId, seatId(hallId, 1, 1)))
                .andExpect(problem(405, "METHOD_NOT_ALLOWED"));
    }

    @DisplayName("GET of an unknown booking returns 404; a malformed id returns 400")
    @Test
    void getUnknownBooking() throws Exception
    {
        mvc.perform(get(BOOKINGS + "/{id}", UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, bearer(customerToken)))
                .andExpect(problem(404, "BOOKING_NOT_FOUND"));

        mvc.perform(get(BOOKINGS + "/not-a-uuid"))
                .andExpect(problem(400, "BAD_REQUEST"));
    }

    // ------------------------------------------------------------ database rules
    // These write rows directly, bypassing the application and Redis, to show
    // that the guarantees do not depend on either of them.

    @DisplayName("The database rejects a second sale of a seat, with no hold and no application check involved")
    @Test
    void databaseRejectsSellingASeatTwice()
    {
        long seatId = seatId(hallId, 1, 2);
        UUID firstBooking = insertBooking(CUSTOMER_ID);
        UUID secondBooking = insertBooking(OTHER_CUSTOMER_ID);
        jdbc.update(INSERT_BOOKING_SEAT, firstBooking, showtimeId, hallId, seatId);

        assertThatThrownBy(() -> jdbc.update(INSERT_BOOKING_SEAT, secondBooking, showtimeId, hallId, seatId))
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("uq_booking_seats_showtime_seat");
    }

    @DisplayName("The database rejects a seat that is not in the showtime's hall")
    @Test
    void databaseRejectsSeatFromAnotherHall() throws Exception
    {
        long otherHallId = createHall("Hall 2");
        long foreignSeatId = seatId(otherHallId, 1, 1);
        UUID booking = insertBooking(CUSTOMER_ID);

        // Claiming the seat is in the showtime's hall: the seat's own hall disagrees.
        assertThatThrownBy(() -> jdbc.update(INSERT_BOOKING_SEAT, booking, showtimeId, hallId, foreignSeatId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_booking_seats_seat");

        // Claiming the showtime runs in the seat's hall: the showtime's own hall disagrees.
        assertThatThrownBy(() -> jdbc.update(INSERT_BOOKING_SEAT, booking, showtimeId, otherHallId, foreignSeatId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_booking_seats_showtime");
    }

    @DisplayName("The database rejects a seat row that belongs to a different showtime than its booking")
    @Test
    void databaseRejectsSeatRowOfAnotherShowtime() throws Exception
    {
        long laterShowtimeId = createShowtime(movieId, hallId, "2030-01-15T21:00:00Z", "2030-01-15T23:00:00Z");
        long seatId = seatId(hallId, 1, 1);
        UUID booking = insertBooking(CUSTOMER_ID);

        assertThatThrownBy(() -> jdbc.update(INSERT_BOOKING_SEAT, booking, laterShowtimeId, hallId, seatId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_booking_seats_booking");
    }

    @DisplayName("The database rejects a booking for an account that does not exist")
    @Test
    void databaseRejectsUnknownUser()
    {
        assertThatThrownBy(() -> insertBooking(UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_bookings_user");
    }

    private UUID insertBooking(UUID userId)
    {
        UUID id = UUID.randomUUID();
        jdbc.update(INSERT_BOOKING, id, showtimeId, hallId, userId);
        return id;
    }
}
