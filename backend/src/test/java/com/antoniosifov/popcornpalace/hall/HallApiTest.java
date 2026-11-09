package com.antoniosifov.popcornpalace.hall;

import com.antoniosifov.popcornpalace.support.ApiIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HallApiTest extends ApiIntegrationTest
{
    @DisplayName("POST creates a hall and every seat of its layout")
    @Test
    void createsHallWithSeats() throws Exception
    {
        postJson(HALLS, hallJson("Hall 1"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/v1/halls/1")))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Hall 1"))
                .andExpect(jsonPath("$.rowCount").value(3))
                .andExpect(jsonPath("$.seatCount").value(DEFAULT_SEAT_COUNT))
                .andExpect(jsonPath("$.rows.length()").value(3))
                .andExpect(jsonPath("$.rows[0].row").value(1))
                .andExpect(jsonPath("$.rows[0].category").value("STANDARD"))
                .andExpect(jsonPath("$.rows[0].seats").value(5))
                .andExpect(jsonPath("$.rows[2].row").value(3))
                .andExpect(jsonPath("$.rows[2].category").value("VIP"))
                .andExpect(jsonPath("$.rows[2].seats").value(4));

        assertThat(countSeats(1)).isEqualTo(DEFAULT_SEAT_COUNT);
        // Rows are numbered across sections; seats restart at 1 in every row.
        assertThat(jdbc.queryForObject(
                "SELECT category FROM seats WHERE hall_id = 1 AND row_number = 3 AND seat_number = 4", String.class))
                .isEqualTo("VIP");
    }

    @DisplayName("The largest allowed hall (1000 seats) is created in one request")
    @Test
    void createsLargestHall() throws Exception
    {
        postJson(HALLS, hallJson("Arena", """
                [{"rows": 20, "seatsPerRow": 50, "category": "STANDARD"}]"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rowCount").value(20))
                .andExpect(jsonPath("$.seatCount").value(1000));

        assertThat(countSeats(1)).isEqualTo(1000);
    }

    @DisplayName("A hall over the size limit is rejected")
    @Test
    void rejectsTooLargeHall() throws Exception
    {
        postJson(HALLS, hallJson("Stadium", """
                [{"rows": 30, "seatsPerRow": 50, "category": "STANDARD"}]"""))
                .andExpect(problem(400, "HALL_TOO_LARGE"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM halls", Long.class)).isZero();
    }

    @DisplayName("An invalid layout is rejected with the path of each bad field")
    @Test
    void rejectsInvalidLayout() throws Exception
    {
        postJson(HALLS, hallJson("  ", "[]"))
                .andExpect(problem(400, "VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItems("name", "sections")));

        postJson(HALLS, hallJson("Hall 1", """
                [{"rows": 0, "seatsPerRow": 51}]"""))
                .andExpect(problem(400, "VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field",
                        hasItems("sections[0].rows", "sections[0].seatsPerRow", "sections[0].category")));
    }

    @DisplayName("An unknown seat category is rejected")
    @Test
    void rejectsUnknownCategory() throws Exception
    {
        postJson(HALLS, hallJson("Hall 1", """
                [{"rows": 1, "seatsPerRow": 5, "category": "GOLD"}]"""))
                .andExpect(problem(400, "MALFORMED_REQUEST"));
    }

    @DisplayName("Two halls cannot share a name")
    @Test
    void rejectsDuplicateName() throws Exception
    {
        createHall("Hall 1");

        postJson(HALLS, hallJson(" Hall   1 "))
                .andExpect(problem(409, "HALL_NAME_TAKEN"));
    }

    @DisplayName("GET returns a hall with its layout, and 404 for an unknown id")
    @Test
    void getsHall() throws Exception
    {
        long id = createHall("Hall 1");

        mvc.perform(get(HALLS + "/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Hall 1"))
                .andExpect(jsonPath("$.seatCount").value(DEFAULT_SEAT_COUNT))
                .andExpect(jsonPath("$.rows.length()").value(3));

        mvc.perform(get(HALLS + "/{id}", 999))
                .andExpect(problem(404, "HALL_NOT_FOUND"));
    }

    @DisplayName("The list is paginated, sorted by name, and leaves out the layout")
    @Test
    void listsHalls() throws Exception
    {
        createHall("IMAX");
        createHall("Hall 1");

        mvc.perform(get(HALLS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].name").value("Hall 1"))
                .andExpect(jsonPath("$.content[0].seatCount").value(DEFAULT_SEAT_COUNT))
                .andExpect(jsonPath("$.content[0].rows").doesNotExist())
                .andExpect(jsonPath("$.content[1].name").value("IMAX"));
    }

    @DisplayName("DELETE removes a hall together with its seats")
    @Test
    void deletesHallAndSeats() throws Exception
    {
        long id = createHall("Hall 1");

        mvc.perform(delete(HALLS + "/{id}", id)).andExpect(status().isNoContent());

        mvc.perform(get(HALLS + "/{id}", id)).andExpect(problem(404, "HALL_NOT_FOUND"));
        assertThat(countSeats(id)).isZero();
    }

    @DisplayName("A hall with showtimes cannot be deleted")
    @Test
    void doesNotDeleteHallWithShowtimes() throws Exception
    {
        long hallId = createHall("Hall 1");
        long movieId = createMovie("Interstellar");
        createShowtime(movieId, hallId, "2030-01-15T18:00:00Z", "2030-01-15T20:49:00Z");

        mvc.perform(delete(HALLS + "/{id}", hallId)).andExpect(problem(409, "HALL_HAS_SHOWTIMES"));

        assertThat(countSeats(hallId)).isEqualTo(DEFAULT_SEAT_COUNT);
    }

    @DisplayName("DELETE of an unknown id returns 404")
    @Test
    void deleteUnknownHallReturnsNotFound() throws Exception
    {
        mvc.perform(delete(HALLS + "/{id}", 999)).andExpect(problem(404, "HALL_NOT_FOUND"));
    }

    private long countSeats(long hallId)
    {
        return jdbc.queryForObject("SELECT count(*) FROM seats WHERE hall_id = ?", Long.class, hallId);
    }
}
