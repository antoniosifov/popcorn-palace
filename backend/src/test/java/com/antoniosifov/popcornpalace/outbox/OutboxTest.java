package com.antoniosifov.popcornpalace.outbox;

import com.antoniosifov.popcornpalace.payment.FakePaymentProvider;
import com.antoniosifov.popcornpalace.support.ApiIntegrationTest;
import com.antoniosifov.popcornpalace.support.KafkaTestConsumer;
import com.antoniosifov.popcornpalace.support.SimultaneousRequests;
import com.jayway.jsonpath.JsonPath;
import io.micrometer.observation.ObservationRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The transactional outbox, end to end: an event is stored with the booking,
 * and the relay delivers it to Kafka.
 */
class OutboxTest extends ApiIntegrationTest
{
    private static final String TOPIC = "popcorn.booking-events";
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    @Autowired
    private OutboxRelay relay;
    @Autowired
    private KafkaContainer kafka;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private OutboxProperties outboxProperties;
    @Autowired
    private Clock clock;
    @Autowired
    private ObservationRegistry observations;
    @Autowired
    private OutboxMetrics outboxMetrics;

    private long hallId;
    private long showtimeId;

    @BeforeEach
    void createShowtime() throws Exception
    {
        long movieId = createMovie("Interstellar");
        hallId = createHall("Hall 1");
        showtimeId = createShowtime(movieId, hallId, "2030-01-15T18:00:00Z", "2030-01-15T20:49:00Z");
    }

    @DisplayName("A booking writes its event to the outbox, and the relay delivers it to Kafka once")
    @Test
    void bookingEventReachesKafka() throws Exception
    {
        String bookingId = createBooking(showtimeId, seatId(hallId, 1, 1), seatId(hallId, 3, 1));

        // Stored with the booking, not yet sent.
        assertThat(jdbc.queryForMap("SELECT event_type, message_key, published_at IS NULL AS waiting FROM outbox_events"))
                .containsEntry("event_type", "BookingConfirmed")
                .containsEntry("message_key", bookingId)
                .containsEntry("waiting", true);

        assertThat(relay.publishPending()).isEqualTo(1);

        List<ConsumerRecord<String, String>> messages = readFromKafka(bookingId, 1);
        assertThat(messages).hasSize(1);
        ConsumerRecord<String, String> message = messages.get(0);
        assertThat(message.key()).isEqualTo(bookingId);
        assertThat(KafkaTestConsumer.header(message, OutboxRelay.EVENT_TYPE_HEADER)).isEqualTo("BookingConfirmed");

        // The event carries everything a consumer needs, so it never has to call the API back.
        String event = message.value();
        // Published exactly as the application serialized it: fields in their declared
        // order and amounts with their cents, not re-formatted by the database on the way.
        assertThat(event).startsWith("{\"eventId\":").contains("\"totalPrice\":36.50");
        assertThat(JsonPath.<String>read(event, "$.eventId"))
                .isEqualTo(KafkaTestConsumer.header(message, OutboxRelay.EVENT_ID_HEADER));
        assertThat(JsonPath.<String>read(event, "$.type")).isEqualTo("BookingConfirmed");
        assertThat(JsonPath.<Integer>read(event, "$.version")).isEqualTo(1);
        assertThat(JsonPath.<String>read(event, "$.booking.id")).isEqualTo(bookingId);
        assertThat(JsonPath.<String>read(event, "$.booking.status")).isEqualTo("CONFIRMED");
        assertThat(JsonPath.<String>read(event, "$.booking.movieTitle")).isEqualTo("Interstellar");
        assertThat(JsonPath.<String>read(event, "$.booking.hallName")).isEqualTo("Hall 1");
        assertThat(JsonPath.<String>read(event, "$.booking.startTime")).isEqualTo("2030-01-15T18:00:00Z");
        assertThat(JsonPath.<Integer>read(event, "$.booking.seats.length()")).isEqualTo(2);
        assertThat(JsonPath.<String>read(event, "$.booking.seats[1].category")).isEqualTo("VIP");
        assertThat(JsonPath.<Double>read(event, "$.booking.totalPrice")).isEqualTo(36.5);
        assertThat(JsonPath.<String>read(event, "$.booking.currency")).isEqualTo("USD");
        assertThat(JsonPath.<String>read(event, "$.customer.id")).isEqualTo(CUSTOMER_ID.toString());
        assertThat(JsonPath.<String>read(event, "$.customer.email")).isEqualTo("customer@test.local");

        // Marked as sent, so the next run has nothing to do.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE published_at IS NULL", Long.class)).isZero();
        assertThat(relay.publishPending()).isZero();
    }

    @DisplayName("A cancellation is published after the confirmation, to the same partition, so consumers see them in order")
    @Test
    void cancellationFollowsConfirmationInOrder() throws Exception
    {
        String bookingId = createBooking(showtimeId, seatId(hallId, 1, 1));
        mvc.perform(post(BOOKINGS + "/" + bookingId + "/cancel").header(HttpHeaders.AUTHORIZATION, bearer(customerToken)))
                .andExpect(status().isOk());

        assertThat(relay.publishPending()).isEqualTo(2);

        List<ConsumerRecord<String, String>> messages = readFromKafka(bookingId, 2);
        assertThat(messages).extracting(message -> KafkaTestConsumer.header(message, OutboxRelay.EVENT_TYPE_HEADER))
                .containsExactly("BookingConfirmed", "BookingCancelled");
        assertThat(messages.get(0).partition()).isEqualTo(messages.get(1).partition());
        assertThat(messages.get(0).offset()).isLessThan(messages.get(1).offset());
        assertThat(JsonPath.<String>read(messages.get(1).value(), "$.booking.status")).isEqualTo("CANCELLED");
    }

    @DisplayName("If the booking is rolled back, no event is left behind")
    @Test
    void noEventWithoutABooking() throws Exception
    {
        long seat = seatId(hallId, 1, 1);
        String holdId = createHold(customerToken, showtimeId, seat);
        // Make the booking fail after the charge: the seat is sold behind the hold's back.
        UUID otherBooking = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO bookings (id, showtime_id, hall_id, user_id, total_price, created_at)
                VALUES (?, ?, ?, ?, 14.50, now())""", otherBooking, showtimeId, hallId, OTHER_CUSTOMER_ID);
        jdbc.update("""
                INSERT INTO booking_seats (booking_id, showtime_id, hall_id, seat_id, price)
                VALUES (?, ?, ?, ?, 14.50)""", otherBooking, showtimeId, hallId, seat);

        confirm(customerToken, showtimeId, holdId, UUID.randomUUID().toString(), FakePaymentProvider.TOKEN_OK)
                .andExpect(problem(409, "SEAT_ALREADY_BOOKED"));

        // The event was written in the transaction that failed, so it rolled back with it.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events", Long.class)).isZero();
    }

    @DisplayName("When Kafka does not acknowledge, events stay in the outbox and are delivered on a later run")
    @Test
    @SuppressWarnings("unchecked")
    void eventsSurviveAKafkaOutage() throws Exception
    {
        String bookingId = createBooking(showtimeId, seatId(hallId, 1, 1));

        KafkaOperations<String, String> unreachableKafka = mock(KafkaOperations.class);
        when(unreachableKafka.send(any(org.apache.kafka.clients.producer.ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unreachable")));
        OutboxRelay relayDuringOutage =
                new OutboxRelay(jdbcTemplate, unreachableKafka, transactionManager, outboxProperties, observations, outboxMetrics, clock);

        assertThatThrownBy(relayDuringOutage::publishPending).isInstanceOf(OutboxRelay.OutboxPublishException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE published_at IS NULL", Long.class)).isEqualTo(1);

        // Kafka is back.
        assertThat(relay.publishPending()).isEqualTo(1);
        assertThat(readFromKafka(bookingId, 1)).hasSize(1);
    }

    @DisplayName("Several relays running at once deliver every event exactly once, and in order")
    @Test
    void concurrentRelaysDeliverOnceAndInOrder() throws Exception
    {
        String topic = "test.outbox-" + UUID.randomUUID();
        int events = 60;
        for (int i = 0; i < events; i++) {
            jdbc.update("""
                            INSERT INTO outbox_events (event_id, topic, message_key, event_type, payload, created_at)
                            VALUES (?, ?, ?, 'TestEvent', ?::json, now())""",
                    UUID.randomUUID(), topic, "key-" + (i % 5), "{\"n\": " + i + "}");
        }
        // Small batches, so the relays have to take turns many times.
        OutboxProperties smallBatches = new OutboxProperties(Duration.ofHours(1), 7, Duration.ofSeconds(10), Duration.ofDays(7), Duration.ofMinutes(2));
        OutboxRelay smallBatchRelay = new OutboxRelay(jdbcTemplate, realKafka, transactionManager, smallBatches, observations, outboxMetrics, clock);

        // Four "instances" keep publishing until each finds nothing left to do.
        List<Integer> publishedByEachRelay = SimultaneousRequests.run(4, () -> {
            int total = 0;
            int idleRuns = 0;
            while (idleRuns < 3) {
                int published = smallBatchRelay.publishPending();
                total += published;
                idleRuns = published == 0 ? idleRuns + 1 : 0;
                if (published == 0) {
                    Thread.sleep(20);
                }
            }
            return total;
        });

        // Together they published every event, and none twice.
        assertThat(publishedByEachRelay.stream().mapToInt(Integer::intValue).sum()).isEqualTo(events);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE published_at IS NULL", Long.class)).isZero();
        List<ConsumerRecord<String, String>> messages = KafkaTestConsumer.read(
                kafka.getBootstrapServers(), topic, message -> true, events + 1, Duration.ofSeconds(8));
        assertThat(messages).hasSize(events);
        assertThat(messages).extracting(message -> KafkaTestConsumer.header(message, OutboxRelay.EVENT_ID_HEADER))
                .doesNotHaveDuplicates();

        // Within one key, messages arrive in the order the events were written,
        // even though different relays published different batches.
        Map<String, List<Integer>> numbersByKey = new HashMap<>();
        for (ConsumerRecord<String, String> message : messages) {
            numbersByKey.computeIfAbsent(message.key(), key -> new ArrayList<>())
                    .add(JsonPath.<Integer>read(message.value(), "$.n"));
        }
        assertThat(numbersByKey).hasSize(5);
        numbersByKey.values().forEach(numbers -> assertThat(numbers).isSorted().hasSize(events / 5));
    }

    @Autowired
    private KafkaOperations<String, String> realKafka;

    private List<ConsumerRecord<String, String>> readFromKafka(String bookingId, int expected)
    {
        return KafkaTestConsumer.readByKey(kafka.getBootstrapServers(), TOPIC, bookingId, expected, TIMEOUT);
    }
}
