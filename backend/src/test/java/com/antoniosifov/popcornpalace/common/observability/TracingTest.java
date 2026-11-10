package com.antoniosifov.popcornpalace.common.observability;

import com.antoniosifov.popcornpalace.outbox.OutboxRelay;
import com.antoniosifov.popcornpalace.support.ApiIntegrationTest;
import com.antoniosifov.popcornpalace.support.KafkaTestConsumer;
import com.jayway.jsonpath.JsonPath;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * One trace from the request that books the seats to the Kafka message that
 * announces the booking, across the outbox and the relay's own thread.
 */
class TracingTest extends ApiIntegrationTest
{
    private static final String TOPIC = "popcorn.booking-events";
    private static final String TRACE_ID = "[0-9a-f]{32}";
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    @Autowired
    private OutboxRelay relay;
    @Autowired
    private KafkaContainer kafka;

    private long hallId;
    private long showtimeId;

    @BeforeEach
    void createShowtime() throws Exception
    {
        long movieId = createMovie("Interstellar");
        hallId = createHall("Hall 1");
        showtimeId = createShowtime(movieId, hallId, "2030-01-15T18:00:00Z", "2030-01-15T20:49:00Z");
    }

    @DisplayName("The Kafka message of a booking belongs to the trace of the request that made it")
    @Test
    void kafkaMessageContinuesTheRequestTrace() throws Exception
    {
        String holdId = createHold(customerToken, showtimeId, seatId(hallId, 1, 1));
        MvcResult paid = confirm(customerToken, showtimeId, holdId).andExpect(status().isCreated()).andReturn();
        String traceId = paid.getResponse().getHeader(TraceIdFilter.HEADER);
        assertThat(traceId).matches(TRACE_ID);
        String bookingId = JsonPath.read(paid.getResponse().getContentAsString(), "$.id");

        // Remembered with the event (W3C traceparent: version-traceId-spanId-flags) ...
        String traceParent = jdbc.queryForObject("SELECT trace_parent FROM outbox_events", String.class);
        assertThat(traceParent).startsWith("00-" + traceId + "-");

        // ... and sent along with the message, so the notifications service continues the same trace.
        relay.publishPending();
        List<ConsumerRecord<String, String>> messages =
                KafkaTestConsumer.readByKey(kafka.getBootstrapServers(), TOPIC, bookingId, 1, TIMEOUT);
        assertThat(messages).singleElement().satisfies(message ->
                assertThat(KafkaTestConsumer.header(message, "traceparent")).startsWith("00-" + traceId + "-"));
    }

    @DisplayName("Every response names its trace, and an error response repeats the id in the body")
    @Test
    void responsesCarryTheTraceId() throws Exception
    {
        MvcResult result = anonymous.perform(get(SHOWTIMES + "/999"))
                .andExpect(problem(404, "SHOWTIME_NOT_FOUND"))
                .andReturn();

        String traceId = result.getResponse().getHeader(TraceIdFilter.HEADER);
        assertThat(traceId).matches(TRACE_ID);
        assertThat(JsonPath.<String>read(result.getResponse().getContentAsString(), "$.traceId")).isEqualTo(traceId);
    }

    @DisplayName("A request refused by security still names its trace")
    @Test
    void refusedRequestsCarryTheTraceId() throws Exception
    {
        MvcResult result = anonymous.perform(get(BOOKINGS))
                .andExpect(problem(401, "UNAUTHENTICATED"))
                .andReturn();

        assertThat(result.getResponse().getHeader(TraceIdFilter.HEADER)).matches(TRACE_ID);
    }
}
