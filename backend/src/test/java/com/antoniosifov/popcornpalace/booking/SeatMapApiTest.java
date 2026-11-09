package com.antoniosifov.popcornpalace.booking;

import com.antoniosifov.popcornpalace.support.ApiIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeatMapApiTest extends ApiIntegrationTest
{
    private long movieId;
    private long hallId;
    private long showtimeId;

    @BeforeEach
    void createShowtime() throws Exception
    {
        movieId = createMovie("Interstellar");
        hallId = createHall("Hall 1");
        showtimeId = createShowtime(movieId, hallId, "2030-01-15T18:00:00Z", "2030-01-15T20:49:00Z");
    }

    @DisplayName("The seat map lists every seat row by row with its price and status")
    @Test
    void showsEverySeatWithPriceAndStatus() throws Exception
    {
        long bookedSeatId = seatId(hallId, 1, 2);
        createBooking(showtimeId, bookedSeatId);

        mvc.perform(get(SHOWTIMES + "/{id}/seats", showtimeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.showtimeId").value(showtimeId))
                .andExpect(jsonPath("$.hallId").value(hallId))
                .andExpect(jsonPath("$.hallName").value("Hall 1"))
                .andExpect(jsonPath("$.totalSeats").value(DEFAULT_SEAT_COUNT))
                .andExpect(jsonPath("$.availableSeats").value(DEFAULT_SEAT_COUNT - 1))
                .andExpect(jsonPath("$.rows.length()").value(3))
                // Row 1: five standard seats, the second one is taken.
                .andExpect(jsonPath("$.rows[0].row").value(1))
                .andExpect(jsonPath("$.rows[0].seats.length()").value(5))
                .andExpect(jsonPath("$.rows[0].seats[0].number").value(1))
                .andExpect(jsonPath("$.rows[0].seats[0].category").value("STANDARD"))
                .andExpect(jsonPath("$.rows[0].seats[0].price").value(14.5))
                .andExpect(jsonPath("$.rows[0].seats[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.rows[0].seats[1].seatId").value(bookedSeatId))
                .andExpect(jsonPath("$.rows[0].seats[1].status").value("BOOKED"))
                // Row 3: four VIP seats at the VIP price.
                .andExpect(jsonPath("$.rows[2].row").value(3))
                .andExpect(jsonPath("$.rows[2].seats.length()").value(4))
                .andExpect(jsonPath("$.rows[2].seats[3].number").value(4))
                .andExpect(jsonPath("$.rows[2].seats[3].category").value("VIP"))
                .andExpect(jsonPath("$.rows[2].seats[3].price").value(22.0));
    }

    @DisplayName("A booking marks the seat only in its own showtime")
    @Test
    void bookingDoesNotAffectAnotherShowtimeInTheSameHall() throws Exception
    {
        long laterShowtimeId = createShowtime(movieId, hallId, "2030-01-15T21:00:00Z", "2030-01-15T23:00:00Z");
        createBooking(showtimeId, seatId(hallId, 1, 2));

        mvc.perform(get(SHOWTIMES + "/{id}/seats", laterShowtimeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableSeats").value(DEFAULT_SEAT_COUNT))
                .andExpect(jsonPath("$.rows[*].seats[*].status", everyItem(is("AVAILABLE"))));
    }

    @DisplayName("The seat map of an unknown showtime returns 404")
    @Test
    void unknownShowtime() throws Exception
    {
        mvc.perform(get(SHOWTIMES + "/{id}/seats", 999))
                .andExpect(problem(404, "SHOWTIME_NOT_FOUND"));
    }
}
