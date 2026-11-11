package com.antoniosifov.popcornpalace.support;

import com.antoniosifov.popcornpalace.TestcontainersConfiguration;
import com.antoniosifov.popcornpalace.auth.TokenService;
import com.antoniosifov.popcornpalace.common.observability.TraceIdFilter;
import com.antoniosifov.popcornpalace.payment.FakePaymentProvider;
import com.antoniosifov.popcornpalace.user.Role;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.servlet.Filter;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.filter.ServerHttpObservationFilter;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class for tests that call the API over HTTP against a real PostgreSQL
 * and a real Redis (started once through Testcontainers and shared by all test
 * classes). Every test starts from empty tables, an empty Redis and three
 * accounts, so tests do not depend on each other.
 *
 * <p>Most tests are about business rules, not permissions, so {@link #mvc}
 * sends every request as the administrator unless the request sets its own
 * {@code Authorization} header. Use {@link #anonymous} for requests without
 * credentials and {@link #bearer} with {@link #customerToken} to act as a customer.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
public abstract class ApiIntegrationTest
{
    protected static final String AUTH = "/api/v1/auth";
    protected static final String MOVIES = "/api/v1/movies";
    protected static final String HALLS = "/api/v1/halls";
    protected static final String SHOWTIMES = "/api/v1/showtimes";
    protected static final String BOOKINGS = "/api/v1/bookings";

    protected static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    protected static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    protected static final UUID OTHER_CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c2");

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

    @Autowired
    protected TokenService tokenService;

    @Autowired
    private TraceIdFilter traceIdFilter;

    @Autowired
    protected StringRedisTemplate redis;

    /** The sandbox payment provider; tests read what it "charged" and make it fail. */
    @Autowired
    protected FakePaymentProvider paymentProvider;

    /** Sends requests as the administrator by default. */
    protected MockMvc mvc;
    /** Sends requests with no credentials. */
    protected MockMvc anonymous;
    protected JdbcTemplate jdbc;

    protected String adminToken;
    protected String customerToken;
    protected String otherCustomerToken;

    @BeforeEach
    void resetState()
    {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("TRUNCATE TABLE outbox_events, booking_seats, bookings, payments, showtime_prices, showtimes, seats, halls, movies, users RESTART IDENTITY CASCADE");
        paymentProvider.reset();
        // Seat holds and rate-limit counters live in Redis.
        redis.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });

        // Inserted directly: hashing three passwords before every test would be
        // slow, and these accounts only ever authenticate with tokens.
        insertUser(ADMIN_ID, "admin@test.local", "Admin", Role.ADMIN);
        insertUser(CUSTOMER_ID, "customer@test.local", "Customer", Role.CUSTOMER);
        insertUser(OTHER_CUSTOMER_ID, "other@test.local", "Other customer", Role.CUSTOMER);
        adminToken = tokenService.issue(ADMIN_ID, Role.ADMIN, 0).value();
        customerToken = tokenService.issue(CUSTOMER_ID, Role.CUSTOMER, 0).value();
        otherCustomerToken = tokenService.issue(OTHER_CUSTOMER_ID, Role.CUSTOMER, 0).value();

        // A real server adds these filters by itself; MockMvc only adds what it is told to.
        Filter[] filters = {observationFilter(), traceIdFilter};
        anonymous = MockMvcBuilders.webAppContextSetup(context).addFilters(filters).apply(springSecurity()).build();
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(filters)
                .apply(springSecurity())
                .defaultRequest(get("/").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .build();
    }

    /** The filter that starts a trace and times every request. */
    private Filter observationFilter()
    {
        return context.getBeansOfType(FilterRegistrationBean.class).values().stream()
                .map(registration -> (Filter) registration.getFilter())
                .filter(ServerHttpObservationFilter.class::isInstance)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("The request observation filter is not registered"));
    }

    protected void insertUser(UUID id, String email, String displayName, Role role)
    {
        jdbc.update("INSERT INTO users (id, email, password_hash, display_name, role, created_at) VALUES (?, ?, '!', ?, ?, now())",
                id, email, displayName, role.name());
    }

    // --- requests ---

    /** Value of the Authorization header for the given access token. */
    protected static String bearer(String token)
    {
        return "Bearer " + token;
    }

    /** POST as the administrator. */
    protected ResultActions postJson(String url, String json) throws Exception
    {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** POST with the given access token. */
    protected ResultActions postJsonAs(String token, String url, String json) throws Exception
    {
        return mvc.perform(post(url).header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** PUT as the administrator. */
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

    protected static String holdJson(long... seatIds)
    {
        return "{\"seatIds\": [%s]}".formatted(
                Arrays.stream(seatIds).mapToObj(String::valueOf).collect(Collectors.joining(", ")));
    }

    protected static String holdsUrl(long showtimeId)
    {
        return SHOWTIMES + "/" + showtimeId + "/holds";
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

    /** Holds the seats with the given token and returns the hold id. */
    protected String createHold(String token, long showtimeId, long... seatIds) throws Exception
    {
        String body = postJsonAs(token, holdsUrl(showtimeId), holdJson(seatIds))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    /** Pays for a hold with a card that works and returns the id of the booking it became. */
    protected String confirmHold(String token, long showtimeId, String holdId) throws Exception
    {
        String body = confirm(token, showtimeId, holdId)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    /** Pays for a hold with a card that works, as a fresh purchase attempt (a new idempotency key). */
    protected ResultActions confirm(String token, long showtimeId, String holdId) throws Exception
    {
        return confirm(token, showtimeId, holdId, UUID.randomUUID().toString(), FakePaymentProvider.TOKEN_OK);
    }

    protected ResultActions confirm(String token, long showtimeId, String holdId,
                                    String idempotencyKey, String paymentToken) throws Exception
    {
        return mvc.perform(post(holdsUrl(showtimeId) + "/" + holdId + "/confirm")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentToken\": \"%s\"}".formatted(paymentToken)));
    }

    /** Books the seats as the customer (hold, then pay) and returns the booking id. */
    protected String createBooking(long showtimeId, long... seatIds) throws Exception
    {
        return createBookingAs(customerToken, showtimeId, seatIds);
    }

    protected String createBookingAs(String token, long showtimeId, long... seatIds) throws Exception
    {
        return confirmHold(token, showtimeId, createHold(token, showtimeId, seatIds));
    }

    /** Makes every current hold run out, as if its time to live had passed. */
    protected void expireHolds() throws InterruptedException
    {
        Set<String> keys = redis.keys("hold:*");
        if (keys != null) {
            keys.forEach(key -> redis.expire(key, Duration.ofMillis(1)));
        }
        Thread.sleep(30);
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
