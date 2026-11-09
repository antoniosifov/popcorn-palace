package com.antoniosifov.popcornpalace.support;

import com.antoniosifov.popcornpalace.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class for tests that call the API over HTTP against a real PostgreSQL
 * database (started once through Testcontainers and shared by all test classes).
 * Every test starts from empty tables, so tests do not depend on each other.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
public abstract class ApiIntegrationTest
{
    protected static final String MOVIES = "/api/v1/movies";
    protected static final String HALLS = "/api/v1/halls";
    protected static final String SHOWTIMES = "/api/v1/showtimes";
    protected static final String BOOKINGS = "/api/v1/bookings";

    protected static final UUID USER_ID = UUID.fromString("84438967-f68f-4fa0-b620-0f08217e76af");

    /** The layout of halls made by {@link #createHall}: rows 1-2 have 5 STANDARD seats, row 3 has 4 VIP seats. */
    protected static final String DEFAULT_SECTIONS = """
            [{"rows": 2, "seatsPerRow": 5, "category": "STANDARD"},
             {"rows": 1, "seatsPerRow": 4, "category": "VIP"}]""";
    protected static final int DEFAULT_SEAT_COUNT = 14;

    /** The prices of showtimes made by {@link #createShowtime}. */
    protected static final String DEFAULT_PRICES = """
            {"STANDARD": 14.50, "VIP": 22.00}""";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private DataSource dataSource;

    protected MockMvc mvc;
    protected JdbcTemplate jdbc;

    @BeforeEach
    void resetState()
    {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("TRUNCATE TABLE bookings, showtime_prices, showtimes, seats, halls, movies RESTART IDENTITY CASCADE");
    }

    // --- requests ---

    protected ResultActions postJson(String url, String json) throws Exception
    {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected ResultActions putJson(String url, String json) throws Exception
    {
        return mvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    // --- request bodies ---

    protected static String movieJson(String title)
    {
        return movieJson(title, "Science Fiction", 169, "8.7", 2014);
    }

    protected static String movieJson(String title, String genre, int durationMinutes, String rating, int releaseYear)
    {
        return """
                {"title": "%s", "genre": "%s", "durationMinutes": %d, "rating": %s, "releaseYear": %d}
                """.formatted(title, genre, durationMinutes, rating, releaseYear);
    }

    protected static String hallJson(String name)
    {
        return hallJson(name, DEFAULT_SECTIONS);
    }

    protected static String hallJson(String name, String sections)
    {
        return """
                {"name": "%s", "sections": %s}
                """.formatted(name, sections);
    }

    protected static String showtimeJson(long movieId, long hallId, String startTime, String endTime)
    {
        return showtimeJson(movieId, hallId, startTime, endTime, DEFAULT_PRICES);
    }

    protected static String showtimeJson(long movieId, long hallId, String startTime, String endTime, String prices)
    {
        return """
                {"movieId": %d, "hallId": %d, "startTime": "%s", "endTime": "%s", "prices": %s}
                """.formatted(movieId, hallId, startTime, endTime, prices);
    }

    protected static String bookingJson(long showtimeId, long seatId, UUID userId)
    {
        return """
                {"showtimeId": %d, "seatId": %d, "userId": "%s"}
                """.formatted(showtimeId, seatId, userId);
    }

    // --- test data created through the API ---

    protected long createMovie(String title) throws Exception
    {
        return idOf(postJson(MOVIES, movieJson(title)).andExpect(status().isCreated()));
    }

    /** Creates a hall with the {@link #DEFAULT_SECTIONS} layout. */
    protected long createHall(String name) throws Exception
    {
        return createHall(name, DEFAULT_SECTIONS);
    }

    protected long createHall(String name, String sections) throws Exception
    {
        return idOf(postJson(HALLS, hallJson(name, sections)).andExpect(status().isCreated()));
    }

    protected long createShowtime(long movieId, long hallId, String startTime, String endTime) throws Exception
    {
        return idOf(postJson(SHOWTIMES, showtimeJson(movieId, hallId, startTime, endTime)).andExpect(status().isCreated()));
    }

    protected String createBooking(long showtimeId, long seatId) throws Exception
    {
        String body = postJson(BOOKINGS, bookingJson(showtimeId, seatId, USER_ID))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    /** Looks up the id of the seat at the given position of a hall. */
    protected long seatId(long hallId, int row, int number)
    {
        Long id = jdbc.queryForObject(
                "SELECT id FROM seats WHERE hall_id = ? AND row_number = ? AND seat_number = ?", Long.class, hallId, row, number);
        return id;
    }

    private static long idOf(ResultActions result) throws Exception
    {
        Number id = JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }

    // --- assertions ---

    /** Asserts an RFC 9457 problem response with the given HTTP status and error code. */
    protected static ResultMatcher problem(int httpStatus, String code)
    {
        return result -> {
            status().is(httpStatus).match(result);
            content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON).match(result);
            jsonPath("$.status").value(httpStatus).match(result);
            jsonPath("$.code").value(code).match(result);
            jsonPath("$.title").isNotEmpty().match(result);
            jsonPath("$.detail").isNotEmpty().match(result);
        };
    }
}
