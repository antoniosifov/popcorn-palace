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
    private static final String HALL = "Hall 1";
    private static final String START = "2030-01-15T18:00:00Z";
    private static final String END = "2030-01-15T20:49:00Z";

    private long movieId;

    @BeforeEach
    void createMovie() throws Exception
    {
        movieId = createMovie("Interstellar");
    }

    @DisplayName("POST schedules a showtime and returns it with the movie title")
    @Test
    void schedulesShowtime() throws Exception
    {
        postJson(SHOWTIMES, showtimeJson(movieId, HALL, START, END))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/v1/showtimes/1")))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.movieId").value(movieId))
                .andExpect(jsonPath("$.movieTitle").value("Interstellar"))
                .andExpect(jsonPath("$.theater").value(HALL))
                .andExpect(jsonPath("$.startTime").value(START))
                .andExpect(jsonPath("$.endTime").value(END))
                .andExpect(jsonPath("$.price").value(14.5));
    }

    @DisplayName("Times sent with a UTC offset are stored as the same instant and returned in UTC")
    @Test
    void convertsOffsetTimesToUtc() throws Exception
    {
        postJson(SHOWTIMES, showtimeJson(movieId, HALL, "2030-01-15T20:00:00+02:00", "2030-01-15T22:49:00+02:00"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.startTime").value(START))
                .andExpect(jsonPath("$.endTime").value(END));
    }

    @DisplayName("A showtime for an unknown movie is rejected")
    @Test
    void rejectsUnknownMovie() throws Exception
    {
        postJson(SHOWTIMES, showtimeJson(999, HALL, START, END))
                .andExpect(problem(404, "MOVIE_NOT_FOUND"));
    }

    @DisplayName("The end time must be after the start time")
    @Test
    void rejectsEndBeforeStart() throws Exception
    {
        postJson(SHOWTIMES, showtimeJson(movieId, HALL, END, START))
                .andExpect(problem(400, "VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("endTime"))
                .andExpect(jsonPath("$.errors[0].message").value("must be after startTime"));
    }

    @DisplayName("A negative price is rejected")
    @Test
    void rejectsNegativePrice() throws Exception
    {
        postJson(SHOWTIMES, showtimeJson(movieId, HALL, START, END, "-1.00"))
                .andExpect(problem(400, "VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("price"));
    }

    @DisplayName("Two showtimes cannot overlap in the same theater")
    @Test
    void rejectsOverlapInSameTheater() throws Exception
    {
        createShowtime(movieId, HALL, START, END);

        postJson(SHOWTIMES, showtimeJson(movieId, HALL, "2030-01-15T19:00:00Z", "2030-01-15T21:00:00Z"))
                .andExpect(problem(409, "SHOWTIME_OVERLAP"));
    }

    @DisplayName("A showtime that fully contains another one also counts as an overlap")
    @Test
    void rejectsShowtimeContainingAnother() throws Exception
    {
        createShowtime(movieId, HALL, START, END);

        postJson(SHOWTIMES, showtimeJson(movieId, HALL, "2030-01-15T17:00:00Z", "2030-01-15T22:00:00Z"))
                .andExpect(problem(409, "SHOWTIME_OVERLAP"));
    }

    @DisplayName("The same time slot is free in another theater")
    @Test
    void allowsSameTimeInAnotherTheater() throws Exception
    {
        createShowtime(movieId, HALL, START, END);

        postJson(SHOWTIMES, showtimeJson(movieId, "Hall 2", START, END))
                .andExpect(status().isCreated());
    }

    @DisplayName("A showtime may start exactly when the previous one ends")
    @Test
    void allowsBackToBackShowtimes() throws Exception
    {
        createShowtime(movieId, HALL, START, END);

        postJson(SHOWTIMES, showtimeJson(movieId, HALL, END, "2030-01-15T23:00:00Z"))
                .andExpect(status().isCreated());
    }

    @DisplayName("PUT cannot move a showtime onto another showtime's slot")
    @Test
    void updateRejectsOverlapWithAnotherShowtime() throws Exception
    {
        createShowtime(movieId, HALL, START, END);
        long laterId = createShowtime(movieId, HALL, "2030-01-15T21:00:00Z", "2030-01-15T23:00:00Z");

        putJson(SHOWTIMES + "/" + laterId, showtimeJson(movieId, HALL, "2030-01-15T20:00:00Z", "2030-01-15T22:00:00Z"))
                .andExpect(problem(409, "SHOWTIME_OVERLAP"));
    }

    @DisplayName("PUT may keep the showtime's own slot, for example to change the price")
    @Test
    void updateMayKeepOwnSlot() throws Exception
    {
        long id = createShowtime(movieId, HALL, START, END);

        putJson(SHOWTIMES + "/" + id, showtimeJson(movieId, HALL, START, END, "19.90"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.price").value(19.9));
    }

    @DisplayName("PUT can move a showtime to another movie and theater")
    @Test
    void updatesShowtime() throws Exception
    {
        long id = createShowtime(movieId, HALL, START, END);
        long otherMovieId = createMovie("Alien");

        putJson(SHOWTIMES + "/" + id, showtimeJson(otherMovieId, "Hall 2", START, END))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movieId").value(otherMovieId))
                .andExpect(jsonPath("$.movieTitle").value("Alien"))
                .andExpect(jsonPath("$.theater").value("Hall 2"));
    }

    @DisplayName("PUT of an unknown id returns 404")
    @Test
    void updateUnknownShowtimeReturnsNotFound() throws Exception
    {
        putJson(SHOWTIMES + "/999", showtimeJson(movieId, HALL, START, END))
                .andExpect(problem(404, "SHOWTIME_NOT_FOUND"));
    }

    @DisplayName("GET returns a showtime by id, and 404 for an unknown id")
    @Test
    void getsShowtime() throws Exception
    {
        long id = createShowtime(movieId, HALL, START, END);

        mvc.perform(get(SHOWTIMES + "/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movieTitle").value("Interstellar"));

        mvc.perform(get(SHOWTIMES + "/{id}", 999))
                .andExpect(problem(404, "SHOWTIME_NOT_FOUND"));
    }

    @DisplayName("The list is ordered by start time and can be filtered by movie")
    @Test
    void listsShowtimesFilteredByMovie() throws Exception
    {
        long otherMovieId = createMovie("Alien");
        createShowtime(movieId, HALL, "2030-01-16T18:00:00Z", "2030-01-16T20:00:00Z");
        createShowtime(movieId, HALL, "2030-01-15T18:00:00Z", "2030-01-15T20:00:00Z");
        createShowtime(otherMovieId, "Hall 2", "2030-01-15T10:00:00Z", "2030-01-15T12:00:00Z");

        mvc.perform(get(SHOWTIMES))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].movieTitle").value("Alien"))
                .andExpect(jsonPath("$.content[1].startTime").value("2030-01-15T18:00:00Z"))
                .andExpect(jsonPath("$.content[2].startTime").value("2030-01-16T18:00:00Z"));

        mvc.perform(get(SHOWTIMES).param("movieId", String.valueOf(movieId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].movieId").value(movieId))
                .andExpect(jsonPath("$.content[1].movieId").value(movieId));
    }

    @DisplayName("DELETE removes a showtime")
    @Test
    void deletesShowtime() throws Exception
    {
        long id = createShowtime(movieId, HALL, START, END);

        mvc.perform(delete(SHOWTIMES + "/{id}", id)).andExpect(status().isNoContent());

        mvc.perform(get(SHOWTIMES + "/{id}", id)).andExpect(problem(404, "SHOWTIME_NOT_FOUND"));
    }

    @DisplayName("A showtime with bookings cannot be deleted")
    @Test
    void doesNotDeleteShowtimeWithBookings() throws Exception
    {
        long id = createShowtime(movieId, HALL, START, END);
        createBooking(id, 15);

        mvc.perform(delete(SHOWTIMES + "/{id}", id)).andExpect(problem(409, "SHOWTIME_HAS_BOOKINGS"));

        mvc.perform(get(SHOWTIMES + "/{id}", id)).andExpect(status().isOk());
    }
}
