package com.antoniosifov.popcornpalace.movie;

import com.antoniosifov.popcornpalace.support.ApiIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MovieApiTest extends ApiIntegrationTest
{
    @DisplayName("POST creates a movie and returns it with a Location header")
    @Test
    void createsMovie() throws Exception
    {
        postJson(MOVIES, movieJson("Interstellar"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/v1/movies/1")))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Interstellar"))
                .andExpect(jsonPath("$.genre").value("Science Fiction"))
                .andExpect(jsonPath("$.durationMinutes").value(169))
                .andExpect(jsonPath("$.rating").value(8.7))
                .andExpect(jsonPath("$.releaseYear").value(2014));
    }

    @DisplayName("Extra whitespace in text fields is removed before saving")
    @Test
    void normalizesWhitespace() throws Exception
    {
        postJson(MOVIES, movieJson("  The   Matrix "))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("The Matrix"));
    }

    @DisplayName("Invalid values are rejected with one error per field")
    @Test
    void rejectsInvalidValues() throws Exception
    {
        postJson(MOVIES, movieJson("   ", "", 0, "10.5", 1500))
                .andExpect(problem(400, "VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field",
                        hasItems("title", "genre", "durationMinutes", "rating", "releaseYear")));
    }

    @DisplayName("Missing fields are rejected, not silently defaulted to zero")
    @Test
    void rejectsMissingFields() throws Exception
    {
        postJson(MOVIES, "{}")
                .andExpect(problem(400, "VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field",
                        containsInAnyOrder("title", "genre", "durationMinutes", "rating", "releaseYear")));
    }

    @DisplayName("A rating with more than one decimal place is rejected")
    @Test
    void rejectsTooPreciseRating() throws Exception
    {
        postJson(MOVIES, movieJson("Interstellar", "Science Fiction", 169, "8.75", 2014))
                .andExpect(problem(400, "VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("rating"));
    }

    @DisplayName("A body that is not valid JSON is rejected without leaking parser details")
    @Test
    void rejectsMalformedJson() throws Exception
    {
        postJson(MOVIES, "{\"title\": ")
                .andExpect(problem(400, "MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.detail").value("Request body is missing or malformed"));
    }

    @DisplayName("Two movies cannot share a title")
    @Test
    void rejectsDuplicateTitle() throws Exception
    {
        createMovie("Interstellar");

        postJson(MOVIES, movieJson("Interstellar"))
                .andExpect(problem(409, "MOVIE_TITLE_TAKEN"));
    }

    @DisplayName("GET returns a movie by id")
    @Test
    void getsMovie() throws Exception
    {
        long id = createMovie("Interstellar");

        mvc.perform(get(MOVIES + "/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.title").value("Interstellar"));
    }

    @DisplayName("GET of an unknown id returns 404")
    @Test
    void getUnknownMovieReturnsNotFound() throws Exception
    {
        mvc.perform(get(MOVIES + "/{id}", 999))
                .andExpect(problem(404, "MOVIE_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Movie 999 was not found"))
                .andExpect(jsonPath("$.instance").value("/api/v1/movies/999"));
    }

    @DisplayName("An id that is not a number is a 400, not a 500")
    @Test
    void rejectsNonNumericId() throws Exception
    {
        mvc.perform(get(MOVIES + "/abc"))
                .andExpect(problem(400, "BAD_REQUEST"));
    }

    @DisplayName("The list is paginated and sorted by title by default")
    @Test
    void listsMoviesPaginated() throws Exception
    {
        createMovie("Coco");
        createMovie("Alien");
        createMovie("Brazil");

        mvc.perform(get(MOVIES).param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].title").value("Alien"))
                .andExpect(jsonPath("$.content[1].title").value("Brazil"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));

        mvc.perform(get(MOVIES).param("size", "2").param("page", "1"))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Coco"));
    }

    @DisplayName("The list can be sorted by an allowed field")
    @Test
    void sortsByRequestedField() throws Exception
    {
        postJson(MOVIES, movieJson("Old", "Drama", 100, "7.0", 1950)).andExpect(status().isCreated());
        postJson(MOVIES, movieJson("New", "Drama", 100, "7.0", 2020)).andExpect(status().isCreated());

        mvc.perform(get(MOVIES).param("sort", "releaseYear,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("New"))
                .andExpect(jsonPath("$.content[1].title").value("Old"));
    }

    @DisplayName("Sorting by a field that is not on the allow-list is rejected")
    @Test
    void rejectsUnknownSortField() throws Exception
    {
        mvc.perform(get(MOVIES).param("sort", "secret"))
                .andExpect(problem(400, "INVALID_SORT"));
    }

    @DisplayName("The page size is 20 unless the client asks for another one")
    @Test
    void usesDefaultPageSize() throws Exception
    {
        mvc.perform(get(MOVIES))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(20));
    }

    @DisplayName("The page size is capped, so a client cannot request the whole table")
    @Test
    void capsPageSize() throws Exception
    {
        mvc.perform(get(MOVIES).param("size", "100000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @DisplayName("PUT replaces a movie")
    @Test
    void updatesMovie() throws Exception
    {
        long id = createMovie("Interstellar");

        putJson(MOVIES + "/" + id, movieJson("Interstellar (IMAX)", "Sci-Fi", 170, "9.0", 2015))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.title").value("Interstellar (IMAX)"))
                .andExpect(jsonPath("$.genre").value("Sci-Fi"))
                .andExpect(jsonPath("$.durationMinutes").value(170))
                .andExpect(jsonPath("$.rating").value(9.0))
                .andExpect(jsonPath("$.releaseYear").value(2015));

        mvc.perform(get(MOVIES + "/{id}", id))
                .andExpect(jsonPath("$.title").value("Interstellar (IMAX)"));
    }

    @DisplayName("PUT may keep the movie's own title")
    @Test
    void updateMayKeepOwnTitle() throws Exception
    {
        long id = createMovie("Interstellar");

        putJson(MOVIES + "/" + id, movieJson("Interstellar", "Drama", 169, "8.7", 2014))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.genre").value("Drama"));
    }

    @DisplayName("PUT cannot take the title of another movie")
    @Test
    void updateRejectsTitleOfAnotherMovie() throws Exception
    {
        createMovie("Alien");
        long id = createMovie("Brazil");

        putJson(MOVIES + "/" + id, movieJson("Alien"))
                .andExpect(problem(409, "MOVIE_TITLE_TAKEN"));
    }

    @DisplayName("PUT of an unknown id returns 404")
    @Test
    void updateUnknownMovieReturnsNotFound() throws Exception
    {
        putJson(MOVIES + "/999", movieJson("Interstellar"))
                .andExpect(problem(404, "MOVIE_NOT_FOUND"));
    }

    @DisplayName("DELETE removes a movie")
    @Test
    void deletesMovie() throws Exception
    {
        long id = createMovie("Interstellar");

        mvc.perform(delete(MOVIES + "/{id}", id)).andExpect(status().isNoContent());

        mvc.perform(get(MOVIES + "/{id}", id)).andExpect(problem(404, "MOVIE_NOT_FOUND"));
    }

    @DisplayName("DELETE of an unknown id returns 404")
    @Test
    void deleteUnknownMovieReturnsNotFound() throws Exception
    {
        mvc.perform(delete(MOVIES + "/{id}", 999)).andExpect(problem(404, "MOVIE_NOT_FOUND"));
    }

    @DisplayName("A movie with showtimes cannot be deleted")
    @Test
    void doesNotDeleteMovieWithShowtimes() throws Exception
    {
        long id = createMovie("Interstellar");
        createShowtime(id, "Hall 1", "2030-01-15T18:00:00Z", "2030-01-15T20:49:00Z");

        mvc.perform(delete(MOVIES + "/{id}", id)).andExpect(problem(409, "MOVIE_HAS_SHOWTIMES"));

        mvc.perform(get(MOVIES + "/{id}", id)).andExpect(status().isOk());
    }
}
