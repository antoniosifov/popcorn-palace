package com.antoniosifov.popcornpalace.seatmap;

import com.antoniosifov.popcornpalace.hall.SeatStatusChanged;
import com.antoniosifov.popcornpalace.support.ApiIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The live seat map: a browser opens the event stream, and every change made
 * through the API shows up in it.
 */
class SeatMapStreamTest extends ApiIntegrationTest
{
    private static final Duration PATIENCE = Duration.ofSeconds(10);

    @Autowired
    private SeatMapBroadcaster broadcaster;
    @Autowired
    private JsonMapper jsonMapper;

    private long hallId;
    private long showtimeId;
    private long seatA;
    private long seatB;

    @BeforeEach
    void createShowtime() throws Exception
    {
        long movieId = createMovie("Interstellar");
        hallId = createHall("Hall 1");
        showtimeId = createShowtime(movieId, hallId, "2030-01-15T18:00:00Z", "2030-01-15T20:49:00Z");
        seatA = seatId(hallId, 1, 1);
        seatB = seatId(hallId, 1, 2);
    }

    @AfterEach
    void closeStreams()
    {
        broadcaster.disconnectAll();
    }

    @DisplayName("The stream opens with a connected event and is public")
    @Test
    void opensForAnonymousVisitors() throws Exception
    {
        MvcResult stream = openStream();

        assertThat(stream.getResponse().getContentType()).startsWith("text/event-stream");
        awaitStream(stream, "event:connected", "\"showtimeId\": " + showtimeId);
        assertThat(broadcaster.streamCount(showtimeId)).isEqualTo(1);
    }

    @DisplayName("Holding, releasing, paying and cancelling all show up in the stream, in that order")
    @Test
    void streamsEveryChange() throws Exception
    {
        MvcResult stream = openStream();
        awaitStream(stream, "event:connected");

        String holdId = createHold(customerToken, showtimeId, seatA, seatB);
        awaitStream(stream, "\"status\":\"HELD\"", "\"holdId\":\"" + holdId + "\"", "\"seatIds\":[" + seatA + "," + seatB + "]");

        mvc.perform(delete(holdsUrl(showtimeId) + "/" + holdId).header(HttpHeaders.AUTHORIZATION, bearer(customerToken)))
                .andExpect(status().isNoContent());
        awaitStream(stream, "\"status\":\"AVAILABLE\"", "\"seatIds\":[" + seatA + "," + seatB + "]");

        String bookingId = createBooking(showtimeId, seatA);
        awaitStream(stream, "\"status\":\"BOOKED\"", "\"seatIds\":[" + seatA + "]");

        mvc.perform(post(BOOKINGS + "/" + bookingId + "/cancel").header(HttpHeaders.AUTHORIZATION, bearer(customerToken)))
                .andExpect(status().isOk());
        awaitStream(stream, "\"status\":\"AVAILABLE\"", "\"seatIds\":[" + seatA + "]");

        // Every change, in the order it happened (a purchase is a hold followed by a sale).
        List<String> statuses = eventsIn(stream).stream().map(event -> jsonField(event, "status")).toList();
        assertThat(statuses).containsExactly("HELD", "AVAILABLE", "HELD", "BOOKED", "AVAILABLE");
    }

    @DisplayName("When a hold runs out, the stream reports its seats as available again")
    @Test
    void streamsExpiredHolds() throws Exception
    {
        MvcResult stream = openStream();
        awaitStream(stream, "event:connected");
        createHold(customerToken, showtimeId, seatA);
        awaitStream(stream, "\"status\":\"HELD\"");

        expireHolds();

        // Redis itself announces the expired key; nobody in the application did anything.
        awaitStream(stream, "\"status\":\"AVAILABLE\"", "\"seatIds\":[" + seatA + "]");
    }

    @DisplayName("A change made by another instance of the application reaches this instance's browsers")
    @Test
    void relaysChangesFromOtherInstances() throws Exception
    {
        MvcResult stream = openStream();
        awaitStream(stream, "event:connected");

        // Another instance would publish on the same channel; do what it does.
        UUID holdId = UUID.randomUUID();
        redis.convertAndSend("seatmap:" + showtimeId,
                jsonMapper.writeValueAsString(SeatStatusChanged.held(showtimeId, List.of(seatB), holdId)));

        awaitStream(stream, "\"status\":\"HELD\"", "\"seatIds\":[" + seatB + "]", holdId.toString());
    }

    @DisplayName("Streams of other showtimes are left alone")
    @Test
    void onlyTheRightShowtimeIsNotified() throws Exception
    {
        long otherShowtimeId = createShowtime(createMovie("Alien"), hallId, "2030-01-15T21:00:00Z", "2030-01-15T23:00:00Z");
        MvcResult stream = openStream();
        MvcResult otherStream = anonymous.perform(get(SHOWTIMES + "/" + otherShowtimeId + "/seats/stream"))
                .andExpect(request().asyncStarted()).andReturn();
        awaitStream(stream, "event:connected");
        awaitStream(otherStream, "event:connected");

        createHold(customerToken, showtimeId, seatA);
        awaitStream(stream, "\"status\":\"HELD\"");

        assertThat(otherStream.getResponse().getContentAsString()).doesNotContain("event:seats");
    }

    @DisplayName("A browser that went away is forgotten, and the stream of an unknown showtime is refused")
    @Test
    void dropsClosedStreamsAndUnknownShowtimes() throws Exception
    {
        MvcResult stream = openStream();
        awaitStream(stream, "event:connected");
        assertThat(broadcaster.streamCount(showtimeId)).isEqualTo(1);

        // The browser closes the connection.
        stream.getRequest().getAsyncContext().complete();
        await().atMost(PATIENCE).untilAsserted(() -> assertThat(broadcaster.streamCount(showtimeId)).isZero());

        anonymous.perform(get(SHOWTIMES + "/999/seats/stream"))
                .andExpect(problem(404, "SHOWTIME_NOT_FOUND"));
    }

    // ------------------------------------------------------------ helpers

    private MvcResult openStream() throws Exception
    {
        return anonymous.perform(get(SHOWTIMES + "/" + showtimeId + "/seats/stream"))
                .andExpect(request().asyncStarted())
                .andReturn();
    }

    /** Waits until everything given has been written to the stream. */
    private static void awaitStream(MvcResult stream, String... fragments)
    {
        await().atMost(PATIENCE).untilAsserted(() -> {
            String received = stream.getResponse().getContentAsString();
            for (String fragment : fragments) {
                assertThat(received).contains(fragment);
            }
        });
    }

    /** The JSON bodies of the seats events received so far, in order. */
    private static List<String> eventsIn(MvcResult stream) throws Exception
    {
        return stream.getResponse().getContentAsString().lines()
                .filter(line -> line.startsWith("data:") && line.contains("\"status\""))
                .map(line -> line.substring("data:".length()))
                .toList();
    }

    private String jsonField(String json, String field)
    {
        return jsonMapper.readTree(json).get(field).asString();
    }
}
