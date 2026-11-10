package com.antoniosifov.popcornpalace.showtime;

import com.antoniosifov.popcornpalace.support.ApiIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ShowtimeApiTest extends ApiIntegrationTest
{
    private static final String START = "2030-01-15T18:00:00Z";
    private static final String END = "2030-01-15T20:49:00Z";

    private long movieId;
    private long hallId;

    @BeforeEach
    void createMovieAndHall() throws Exception
    {
        movieId = createMovie("Interstellar");
        hallId = createHall("Hall 1");
    }

    @DisplayName("POST schedules a showtime and returns it with movie, hall and prices")
    @Test
    void schedulesShowtime() throws Exception
    {
        postJson(SHOWTIMES, showtimeJson(movieId, hallId, START, END))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/v1/showtimes/1")))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.movieId").value(movieId))
                .andExpect(jsonPath("$.movieTitle").value("Interstellar"))
                .andExpect(jsonPath("$.hallId").value(hallId))
                .andExpect(jsonPath("$.hallName").value("Hall 1"))
                .andExpect(jsonPath("$.startTime").value(START))
                .andExpect(jsonPath("$.endTime").value(END))
                .andExpect(jsonPath("$.prices.STANDARD").value(14.5))
                .andExpect(jsonPath("$.prices.VIP").value(22.0));
    }

    @DisplayName("Times sent with a UTC offset are stored as the same instant and returned in UTC")
    @Test
    void convertsOffsetTimesToUtc() throws Exception
    {
        postJson(SHOWTIMES, showtimeJson(movieId, hallId, "2030-01-15T20:00:00+02:00", "2030-01-15T22:49:00+02:00"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.startTime").value(START))
                .andExpect(jsonPath("$.endTime").value(END));
    }

    @DisplayName("A showtime for an unknown movie or hall is rejected")
    @Test
    void rejectsUnknownMovieOrHall() throws Exception
    {
        postJson(SHOWTIMES, showtimeJson(999, hallId, START, END))
                .andExpect(problem(404, "MOVIE_NOT_FOUND"));

        postJson(SHOWTIMES, showtimeJson(movieId, 999, START, END))
                .andExpect(problem(404, "HALL_NOT_FOUND"));
    }

    @DisplayName("The end time must be after the start time")
    @Test
    void rejectsEndBeforeStart() throws Exception
    {
        postJson(SHOWTIMES, showtimeJson(movieId, hallId, END, START))
                .andExpect(problem(400, "VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("endTime"))
                .andExpect(jsonPath("$.errors[0].message").value("must be after startTime"));
    }

    @DisplayName("A negative price is rejected and the error names the category")
    @Test
    void rejectsNegativePrice() throws Exception
    {
        postJson(SHOWTIMES, showtimeJson(movieId, hallId, START, END, """
                {"STANDARD": -1.00, "VIP": 22.00}"""))
                .andExpect(problem(400, "VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("prices[STANDARD]"));
    }

    @DisplayName("Every seat category of the hall needs a price")
    @Test
    void rejectsMissingPriceForCategoryOfTheHall() throws Exception
    {
        postJson(SHOWTIMES, showtimeJson(movieId, hallId, START, END, """
                {"STANDARD": 14.50}"""))
                .andExpect(problem(400, "SHOWTIME_PRICE_MISSING"))
                .andExpect(jsonPath("$.detail").value("Hall 'Hall 1' has [VIP] seats, but no price was given for them"));
    }

    @DisplayName("A hall with only standard seats needs only the standard price")
    @Test
    void acceptsPricesForJustTheCategoriesOfTheHall() throws Exception
    {
        long smallHallId = createHall("Studio", """
                [{"rows": 2, "seatsPerRow": 3, "category": "STANDARD"}]""");

        postJson(SHOWTIMES, showtimeJson(movieId, smallHallId, START, END, """
                {"STANDARD": 9.00}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.prices.STANDARD").value(9.0))
                .andExpect(jsonPath("$.prices.VIP").doesNotExist());
    }

    @DisplayName("Two showtimes cannot overlap in the same hall")
    @Test
    void rejectsOverlapInSameHall() throws Exception
    {
        createShowtime(movieId, hallId, START, END);

        postJson(SHOWTIMES, showtimeJson(movieId, hallId, "2030-01-15T19:00:00Z", "2030-01-15T21:00:00Z"))
                .andExpect(problem(409, "SHOWTIME_OVERLAP"));
    }

    @DisplayName("A showtime that fully contains another one also counts as an overlap")
    @Test
    void rejectsShowtimeContainingAnother() throws Exception
    {
        createShowtime(movieId, hallId, START, END);

        postJson(SHOWTIMES, showtimeJson(movieId, hallId, "2030-01-15T17:00:00Z", "2030-01-15T22:00:00Z"))
                .andExpect(problem(409, "SHOWTIME_OVERLAP"));
    }

    @DisplayName("The same time slot is free in another hall")
    @Test
    void allowsSameTimeInAnotherHall() throws Exception
    {
        long otherHallId = createHall("Hall 2");
        createShowtime(movieId, hallId, START, END);

        postJson(SHOWTIMES, showtimeJson(movieId, otherHallId, START, END))
                .andExpect(status().isCreated());
    }

    @DisplayName("A showtime may start exactly when the previous one ends")
    @Test
    void allowsBackToBackShowtimes() throws Exception
    {
        createShowtime(movieId, hallId, START, END);

        postJson(SHOWTIMES, showtimeJson(movieId, hallId, END, "2030-01-15T23:00:00Z"))
                .andExpect(status().isCreated());
    }

    @DisplayName("PUT cannot move a showtime onto another showtime's slot")
    @Test
    void updateRejectsOverlapWithAnotherShowtime() throws Exception
    {
        createShowtime(movieId, hallId, START, END);
        long laterId = createShowtime(movieId, hallId, "2030-01-15T21:00:00Z", "2030-01-15T23:00:00Z");

        putJson(SHOWTIMES + "/" + laterId, showtimeJson(movieId, hallId, "2030-01-15T20:00:00Z", "2030-01-15T22:00:00Z"))
                .andExpect(problem(409, "SHOWTIME_OVERLAP"));
    }

    @DisplayName("PUT may keep the showtime's own slot, for example to change the prices")
    @Test
    void updateMayKeepOwnSlot() throws Exception
    {
        long id = createShowtime(movieId, hallId, START, END);

        putJson(SHOWTIMES + "/" + id, showtimeJson(movieId, hallId, START, END, """
                {"STANDARD": 19.90, "VIP": 30.00}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.prices.STANDARD").value(19.9))
                .andExpect(jsonPath("$.prices.VIP").value(30.0));

        mvc.perform(get(SHOWTIMES + "/{id}", id))
                .andExpect(jsonPath("$.prices.STANDARD").value(19.9));
    }

    @DisplayName("PUT can move a showtime without bookings to another movie and hall")
    @Test
    void updatesShowtime() throws Exception
    {
        long id = createShowtime(movieId, hallId, START, END);
        long otherMovieId = createMovie("Alien");
        long otherHallId = createHall("Hall 2");

        putJson(SHOWTIMES + "/" + id, showtimeJson(otherMovieId, otherHallId, START, END))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movieId").value(otherMovieId))
                .andExpect(jsonPath("$.movieTitle").value("Alien"))
                .andExpect(jsonPath("$.hallId").value(otherHallId))
                .andExpect(jsonPath("$.hallName").value("Hall 2"));
    }

    @DisplayName("A showtime with bookings cannot be moved to another hall")
    @Test
    void doesNotMoveShowtimeWithBookingsToAnotherHall() throws Exception
    {
        long id = createShowtime(movieId, hallId, START, END);
        long otherHallId = createHall("Hall 2");
        createBooking(id, seatId(hallId, 1, 1));

        putJson(SHOWTIMES + "/" + id, showtimeJson(movieId, otherHallId, START, END))
                .andExpect(problem(409, "SHOWTIME_HAS_BOOKINGS"));

        mvc.perform(get(SHOWTIMES + "/{id}", id))
                .andExpect(jsonPath("$.hallId").value(hallId));
    }

    @DisplayName("PUT of an unknown id returns 404")
    @Test
    void updateUnknownShowtimeReturnsNotFound() throws Exception
    {
        putJson(SHOWTIMES + "/999", showtimeJson(movieId, hallId, START, END))
                .andExpect(problem(404, "SHOWTIME_NOT_FOUND"));
    }

    @DisplayName("GET returns a showtime by id, and 404 for an unknown id")
    @Test
    void getsShowtime() throws Exception
    {
        long id = createShowtime(movieId, hallId, START, END);

        mvc.perform(get(SHOWTIMES + "/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movieTitle").value("Interstellar"))
                .andExpect(jsonPath("$.hallName").value("Hall 1"));

        mvc.perform(get(SHOWTIMES + "/{id}", 999))
                .andExpect(problem(404, "SHOWTIME_NOT_FOUND"));
    }

    @DisplayName("The list is ordered by start time and can be filtered by movie")
    @Test
    void listsShowtimesFilteredByMovie() throws Exception
    {
        long otherMovieId = createMovie("Alien");
        long otherHallId = createHall("Hall 2");
        createShowtime(movieId, hallId, "2030-01-16T18:00:00Z", "2030-01-16T20:00:00Z");
        createShowtime(movieId, hallId, "2030-01-15T18:00:00Z", "2030-01-15T20:00:00Z");
        createShowtime(otherMovieId, otherHallId, "2030-01-15T10:00:00Z", "2030-01-15T12:00:00Z");

        mvc.perform(get(SHOWTIMES))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].movieTitle").value("Alien"))
                .andExpect(jsonPath("$.content[0].prices.STANDARD").value(14.5))
                .andExpect(jsonPath("$.content[1].startTime").value("2030-01-15T18:00:00Z"))
                .andExpect(jsonPath("$.content[2].startTime").value("2030-01-16T18:00:00Z"));

        mvc.perform(get(SHOWTIMES).param("movieId", String.valueOf(movieId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].movieId").value(movieId))
                .andExpect(jsonPath("$.content[1].movieId").value(movieId));
    }

    @DisplayName("from and to select the showtimes that start inside a window: from is included, to is not")
    @Test
    void listsShowtimesStartingInsideAWindow() throws Exception
    {
        createShowtime(movieId, hallId, "2030-01-14T21:00:00Z", "2030-01-14T23:00:00Z");
        long first = createShowtime(movieId, hallId, "2030-01-15T00:00:00Z", "2030-01-15T02:00:00Z");
        long second = createShowtime(movieId, hallId, START, END);
        createShowtime(movieId, hallId, "2030-01-16T00:00:00Z", "2030-01-16T02:00:00Z");

        mvc.perform(get(SHOWTIMES).param("from", "2030-01-15T00:00:00Z").param("to", "2030-01-16T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(first))
                .andExpect(jsonPath("$.content[1].id").value(second));
    }

    @DisplayName("Either bound may be left out, and the window combines with the movie filter")
    @Test
    void listsShowtimesWithAnOpenWindow() throws Exception
    {
        long otherMovieId = createMovie("Arrival");
        createShowtime(movieId, hallId, "2030-01-14T18:00:00Z", "2030-01-14T20:00:00Z");
        long later = createShowtime(movieId, hallId, START, END);
        createShowtime(otherMovieId, hallId, "2030-01-16T18:00:00Z", "2030-01-16T20:00:00Z");

        // What is still to come, as a client asks for it: everything from a moment on.
        mvc.perform(get(SHOWTIMES).param("from", "2030-01-15T00:00:00Z"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(later));

        mvc.perform(get(SHOWTIMES).param("to", "2030-01-15T00:00:00Z"))
                .andExpect(jsonPath("$.totalElements").value(1));

        mvc.perform(get(SHOWTIMES).param("from", "2030-01-15T00:00:00Z").param("movieId", String.valueOf(movieId)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(later));
    }

    @DisplayName("A time with a UTC offset is the same instant, and a value that is not a time is a 400")
    @Test
    void windowAcceptsOffsetsAndRejectsNonsense() throws Exception
    {
        createShowtime(movieId, hallId, START, END);

        // 20:00 at +02:00 is 18:00 UTC, the moment the showtime starts.
        mvc.perform(get(SHOWTIMES).param("from", "2030-01-15T20:00:00+02:00"))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get(SHOWTIMES).param("from", "2030-01-15T20:00:01+02:00"))
                .andExpect(jsonPath("$.totalElements").value(0));

        mvc.perform(get(SHOWTIMES).param("from", "tomorrow"))
                .andExpect(problem(400, "BAD_REQUEST"));
    }

    @DisplayName("DELETE removes a showtime and its prices")
    @Test
    void deletesShowtime() throws Exception
    {
        long id = createShowtime(movieId, hallId, START, END);

        mvc.perform(delete(SHOWTIMES + "/{id}", id)).andExpect(status().isNoContent());

        mvc.perform(get(SHOWTIMES + "/{id}", id)).andExpect(problem(404, "SHOWTIME_NOT_FOUND"));
    }

    @DisplayName("A showtime with bookings cannot be deleted")
    @Test
    void doesNotDeleteShowtimeWithBookings() throws Exception
    {
        long id = createShowtime(movieId, hallId, START, END);
        createBooking(id, seatId(hallId, 1, 1));

        mvc.perform(delete(SHOWTIMES + "/{id}", id)).andExpect(problem(409, "SHOWTIME_HAS_BOOKINGS"));

        mvc.perform(get(SHOWTIMES + "/{id}", id)).andExpect(status().isOk());
    }
}
