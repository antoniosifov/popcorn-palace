package com.antoniosifov.popcornpalace;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Drives the API over HTTP against a real PostgreSQL database and checks the
 * request and response shapes documented in the README. It exists to catch
 * contract changes that unit tests with mocked repositories cannot see, such as
 * a framework upgrade changing how JSON is read or written.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ApiContractTest
{
    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp()
    {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @DisplayName("Movie, showtime and booking flow keeps the documented JSON contract")
    @Test
    void bookingFlowKeepsDocumentedContract() throws Exception
    {
        // Unique names keep this test independent of data left by other tests.
        String suffix = UUID.randomUUID().toString();
        String title = "Contract Movie " + suffix;
        String theater = "Contract Hall " + suffix;

        String movieJson = mockMvc.perform(post("/movies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "%s", "genre": "Science Fiction", "duration": 169, "rating": 8.7, "releaseYear": 2014}
                                """.formatted(title)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value(title))
                .andExpect(jsonPath("$.releaseYear").value(2014))
                .andReturn().getResponse().getContentAsString();
        int movieId = JsonPath.read(movieJson, "$.id");

        // The client references the movie by id only: {"movie": {"id": 1}}.
        String showtimeJson = mockMvc.perform(post("/showtimes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(showtimeBody(movieId, theater, "2030-01-15T18:00:00", "2030-01-15T20:49:00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movieId").value(movieId))
                .andExpect(jsonPath("$.theater").value(theater))
                .andExpect(jsonPath("$.startTime").value("2030-01-15T18:00:00"))
                .andExpect(jsonPath("$.endTime").value("2030-01-15T20:49:00"))
                .andExpect(jsonPath("$.price").value(14.5))
                .andReturn().getResponse().getContentAsString();
        int showtimeId = JsonPath.read(showtimeJson, "$.id");

        mockMvc.perform(post("/showtimes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(showtimeBody(movieId, theater, "2030-01-15T19:00:00", "2030-01-15T21:00:00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Showtime overlaps with an existing one in this theater"));

        String bookingBody = """
                {"showtime": {"id": %d}, "seatNumber": 15, "userId": "84438967-f68f-4fa0-b620-0f08217e76af"}
                """.formatted(showtimeId);

        mockMvc.perform(post("/bookings").contentType(MediaType.APPLICATION_JSON).content(bookingBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").isString());

        mockMvc.perform(post("/bookings").contentType(MediaType.APPLICATION_JSON).content(bookingBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Seat 15 is already booked for this showtime"));
    }

    @DisplayName("Missing showtime returns the documented 404 error body")
    @Test
    void missingShowtimeReturnsNotFoundError() throws Exception
    {
        mockMvc.perform(get("/showtimes/{id}", Long.MAX_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.statusCode").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Showtime not found with id: " + Long.MAX_VALUE));
    }

    private static String showtimeBody(int movieId, String theater, String start, String end)
    {
        return """
                {"movie": {"id": %d}, "theater": "%s", "start_time": "%s", "end_time": "%s", "price": 14.50}
                """.formatted(movieId, theater, start, end);
    }
}
