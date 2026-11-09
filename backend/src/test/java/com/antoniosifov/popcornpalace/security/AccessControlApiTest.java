package com.antoniosifov.popcornpalace.security;

import com.antoniosifov.popcornpalace.support.ApiIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Who may call what: anonymous visitors, customers and administrators. */
class AccessControlApiTest extends ApiIntegrationTest
{
    private static final String START = "2030-01-15T18:00:00Z";
    private static final String END = "2030-01-15T20:49:00Z";

    private long movieId;
    private long hallId;
    private long showtimeId;

    @BeforeEach
    void createCatalog() throws Exception
    {
        movieId = createMovie("Interstellar");
        hallId = createHall("Hall 1");
        showtimeId = createShowtime(movieId, hallId, START, END);
    }

    // ------------------------------------------------------------ browsing

    @DisplayName("Anyone can browse movies, halls, showtimes and seat maps without logging in")
    @Test
    void anyoneCanBrowse() throws Exception
    {
        String[] publicPages = {
                MOVIES, MOVIES + "/" + movieId,
                HALLS, HALLS + "/" + hallId,
                SHOWTIMES, SHOWTIMES + "/" + showtimeId, SHOWTIMES + "/" + showtimeId + "/seats"
        };
        for (String page : publicPages) {
            anonymous.perform(get(page)).andExpect(status().isOk());
        }
    }

    @DisplayName("Browsers on another origin may ask permission before sending a token")
    @Test
    void corsPreflightNeedsNoCredentials() throws Exception
    {
        anonymous.perform(options(BOOKINGS)
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"));
    }

    // ------------------------------------------------------------ managing the catalog

    @DisplayName("Without a token, nothing in the catalog can be changed")
    @Test
    void anonymousCannotChangeTheCatalog() throws Exception
    {
        anonymous.perform(post(MOVIES).contentType(MediaType.APPLICATION_JSON).content(movieJson("Alien")))
                .andExpect(problem(401, "UNAUTHENTICATED"));
        anonymous.perform(put(MOVIES + "/" + movieId).contentType(MediaType.APPLICATION_JSON).content(movieJson("Alien")))
                .andExpect(problem(401, "UNAUTHENTICATED"));
        anonymous.perform(delete(MOVIES + "/" + movieId))
                .andExpect(problem(401, "UNAUTHENTICATED"));
        anonymous.perform(post(HALLS).contentType(MediaType.APPLICATION_JSON).content(hallJson("Hall 2")))
                .andExpect(problem(401, "UNAUTHENTICATED"));
        anonymous.perform(delete(SHOWTIMES + "/" + showtimeId))
                .andExpect(problem(401, "UNAUTHENTICATED"));
    }

    @DisplayName("A customer cannot change the catalog")
    @Test
    void customersCannotChangeTheCatalog() throws Exception
    {
        String customer = bearer(customerToken);

        mvc.perform(post(MOVIES).header(HttpHeaders.AUTHORIZATION, customer)
                        .contentType(MediaType.APPLICATION_JSON).content(movieJson("Alien")))
                .andExpect(problem(403, "FORBIDDEN"));
        mvc.perform(put(MOVIES + "/" + movieId).header(HttpHeaders.AUTHORIZATION, customer)
                        .contentType(MediaType.APPLICATION_JSON).content(movieJson("Alien")))
                .andExpect(problem(403, "FORBIDDEN"));
        mvc.perform(delete(MOVIES + "/" + movieId).header(HttpHeaders.AUTHORIZATION, customer))
                .andExpect(problem(403, "FORBIDDEN"));
        mvc.perform(post(HALLS).header(HttpHeaders.AUTHORIZATION, customer)
                        .contentType(MediaType.APPLICATION_JSON).content(hallJson("Hall 2")))
                .andExpect(problem(403, "FORBIDDEN"));
        mvc.perform(delete(HALLS + "/" + hallId).header(HttpHeaders.AUTHORIZATION, customer))
                .andExpect(problem(403, "FORBIDDEN"));
        mvc.perform(post(SHOWTIMES).header(HttpHeaders.AUTHORIZATION, customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(showtimeJson(movieId, hallId, "2030-01-16T18:00:00Z", "2030-01-16T20:00:00Z")))
                .andExpect(problem(403, "FORBIDDEN"));
        mvc.perform(delete(SHOWTIMES + "/" + showtimeId).header(HttpHeaders.AUTHORIZATION, customer))
                .andExpect(problem(403, "FORBIDDEN"));

        // Nothing was changed.
        anonymous.perform(get(MOVIES + "/" + movieId))
                .andExpect(jsonPath("$.title").value("Interstellar"));
        anonymous.perform(get(SHOWTIMES + "/" + showtimeId))
                .andExpect(status().isOk());
    }

    @DisplayName("An administrator can change the catalog")
    @Test
    void administratorsCanChangeTheCatalog() throws Exception
    {
        postJsonAs(adminToken, MOVIES, movieJson("Alien"))
                .andExpect(status().isCreated());
        mvc.perform(delete(SHOWTIMES + "/" + showtimeId).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNoContent());
    }

    // ------------------------------------------------------------ bookings

    @DisplayName("Without a token, seats cannot be booked and bookings cannot be read")
    @Test
    void anonymousCannotBookOrReadBookings() throws Exception
    {
        String bookingId = createBooking(showtimeId, seatId(hallId, 1, 1));

        anonymous.perform(post(BOOKINGS).contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(showtimeId, seatId(hallId, 1, 2))))
                .andExpect(problem(401, "UNAUTHENTICATED"));
        anonymous.perform(get(BOOKINGS))
                .andExpect(problem(401, "UNAUTHENTICATED"));
        anonymous.perform(get(BOOKINGS + "/" + bookingId))
                .andExpect(problem(401, "UNAUTHENTICATED"));
    }

    @DisplayName("A booking belongs to the token's owner, whatever the request body claims")
    @Test
    void bookingBelongsToTheTokenOwner() throws Exception
    {
        postJsonAs(customerToken, BOOKINGS, """
                {"showtimeId": %d, "seatId": %d, "userId": "%s"}
                """.formatted(showtimeId, seatId(hallId, 1, 1), OTHER_CUSTOMER_ID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(CUSTOMER_ID.toString()));
    }

    @DisplayName("A customer sees their own bookings; another customer's booking looks like it does not exist")
    @Test
    void customersSeeOnlyTheirOwnBookings() throws Exception
    {
        String customersBooking = createBooking(showtimeId, seatId(hallId, 1, 1));
        postJsonAs(otherCustomerToken, BOOKINGS, bookingJson(showtimeId, seatId(hallId, 1, 2)))
                .andExpect(status().isCreated());

        mvc.perform(get(BOOKINGS).header(HttpHeaders.AUTHORIZATION, bearer(customerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(customersBooking))
                .andExpect(jsonPath("$.content[0].movieTitle").value("Interstellar"));

        mvc.perform(get(BOOKINGS + "/" + customersBooking).header(HttpHeaders.AUTHORIZATION, bearer(otherCustomerToken)))
                .andExpect(problem(404, "BOOKING_NOT_FOUND"));
    }

    @DisplayName("An administrator can open any booking")
    @Test
    void administratorsCanOpenAnyBooking() throws Exception
    {
        String customersBooking = createBooking(showtimeId, seatId(hallId, 1, 1));

        mvc.perform(get(BOOKINGS + "/" + customersBooking).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(CUSTOMER_ID.toString()));
    }

    @DisplayName("My bookings are listed newest first")
    @Test
    void listsOwnBookingsNewestFirst() throws Exception
    {
        String first = createBooking(showtimeId, seatId(hallId, 1, 1));
        Thread.sleep(5); // bookings are timestamped to the millisecond
        String second = createBooking(showtimeId, seatId(hallId, 1, 2));

        mvc.perform(get(BOOKINGS).header(HttpHeaders.AUTHORIZATION, bearer(customerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(second))
                .andExpect(jsonPath("$.content[1].id").value(first));
    }
}
