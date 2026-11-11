package com.antoniosifov.popcornpalace.notifications;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.mail.internet.MimeMessage;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.kafka.KafkaContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The whole service, as it runs: a booking event is published to a real
 * Kafka, and the test waits for the email to arrive at a mail server.
 */
@SpringBootTest(properties = {
        "spring.mail.host=127.0.0.1",
        "spring.mail.port=3025",
        // Fail fast in tests: two quick retries before the dead-letter topic.
        "app.notifications.max-retries=2",
        "app.notifications.retry-interval=PT0.2S",
        // No trace collector runs during tests.
        "management.tracing.export.otlp.enabled=false"
})
@Import(TestcontainersConfiguration.class)
class NotificationFlowTest
{
    private static final String TOPIC = "popcorn.booking-events";
    private static final String DEAD_LETTER_TOPIC = TOPIC + ".DLT";
    private static final String ACCOUNT_TOPIC = "popcorn.account-events";
    private static final String RESET_LINK = "https://popcorn.example.com/reset-password?token=";
    private static final Duration PATIENCE = Duration.ofSeconds(30);

    /** An in-memory mail server on port 3025 that keeps what it receives. */
    @RegisterExtension
    static final GreenMailExtension mailServer = new GreenMailExtension(ServerSetupTest.SMTP).withPerMethodLifecycle(false);

    @Autowired
    private KafkaOperations<String, String> kafka;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private KafkaContainer kafkaContainer;
    @Autowired
    private WebApplicationContext context;
    @Autowired
    private MeterRegistry meters;

    @BeforeEach
    void startClean() throws Exception
    {
        mailServer.purgeEmailFromAllMailboxes();
        redis.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
    }

    @DisplayName("A confirmed booking produces a ticket email with the showtime, seats and total")
    @Test
    void sendsTicketsForAConfirmedBooking() throws Exception
    {
        UUID bookingId = UUID.randomUUID();

        publish(bookingId, event(UUID.randomUUID(), "BookingConfirmed", bookingId, "anna@example.com"));

        MimeMessage email = awaitEmails(1).get(0);
        assertThat(email.getAllRecipients()[0]).hasToString("anna@example.com");
        assertThat(email.getFrom()[0].toString()).contains("tickets@popcorn-palace.local");
        assertThat(email.getSubject()).isEqualTo("Your tickets for Interstellar");
        assertThat(GreenMailUtil.getBody(email))
                .contains("Hi Anna,")
                .contains("Your booking is confirmed")
                .contains("Interstellar")
                .contains("Hall 1")
                .contains("Tuesday, 15 January 2030, 18:00 (UTC)")
                .contains("Row 9, seat 4 (VIP)  22.00 USD")
                .contains("Row 9, seat 5 (VIP)  22.00 USD")
                .contains("Total paid: 44.00 USD")
                .contains("Booking reference: " + bookingId);
    }

    @DisplayName("A cancelled booking produces a cancellation email that mentions the refund")
    @Test
    void sendsCancellationEmail() throws Exception
    {
        UUID bookingId = UUID.randomUUID();

        publish(bookingId, event(UUID.randomUUID(), "BookingCancelled", bookingId, "anna@example.com"));

        MimeMessage email = awaitEmails(1).get(0);
        assertThat(email.getSubject()).isEqualTo("Your booking for Interstellar was cancelled");
        assertThat(GreenMailUtil.getBody(email))
                .contains("has been cancelled")
                .contains("Refund: 44.00 USD")
                .contains("Booking reference: " + bookingId);
    }

    @DisplayName("The same event delivered twice sends one email")
    @Test
    void ignoresRedeliveredEvent() throws Exception
    {
        UUID bookingId = UUID.randomUUID();
        String sameEvent = event(UUID.randomUUID(), "BookingConfirmed", bookingId, "anna@example.com");

        // At-least-once delivery in action: the publisher sends the event twice.
        publish(bookingId, sameEvent);
        publish(bookingId, sameEvent);
        // A later event in the same partition: once its email arrives, the duplicate
        // before it has certainly been consumed, not merely still waiting.
        publishMarkerAfter(bookingId.toString(), "marker@example.com");

        // Two emails, not three. (The mail server lists messages per mailbox, not in order of arrival.)
        List<MimeMessage> emails = awaitEmails(2);
        assertThat(emails).extracting(email -> email.getAllRecipients()[0].toString())
                .containsExactlyInAnyOrder("anna@example.com", "marker@example.com");
    }

    @DisplayName("An event of a type this service does not know is skipped without stopping the others")
    @Test
    void skipsUnknownEventTypes() throws Exception
    {
        UUID bookingId = UUID.randomUUID();

        publish(bookingId, event(UUID.randomUUID(), "BookingRescheduled", bookingId, "anna@example.com"));
        publishMarkerAfter(bookingId.toString(), "marker@example.com");

        List<MimeMessage> emails = awaitEmails(1);
        assertThat(emails.get(0).getAllRecipients()[0]).hasToString("marker@example.com");
        // Skipped, not failed: it did not go to the dead-letter topic.
        assertThat(readDeadLetters(bookingId.toString(), Duration.ofSeconds(3))).isEmpty();
    }

    @DisplayName("A message that is not a valid event goes to the dead-letter topic and does not block the next one")
    @Test
    void movesMalformedMessagesToTheDeadLetterTopic() throws Exception
    {
        String brokenKey = "broken-" + UUID.randomUUID();
        String incompleteKey = "incomplete-" + UUID.randomUUID();

        kafka.send(TOPIC, brokenKey, "this is not json").get();
        publishMarkerAfter(brokenKey, "after-broken@example.com");
        // Valid JSON, but with nobody to send the email to.
        kafka.send(TOPIC, incompleteKey, """
                {"eventId": "%s", "type": "BookingConfirmed", "booking": {"id": "%s"}}
                """.formatted(UUID.randomUUID(), UUID.randomUUID())).get();
        publishMarkerAfter(incompleteKey, "after-incomplete@example.com");

        // The valid event queued behind each bad message, in the same partition, was still processed ...
        assertThat(awaitEmails(2)).extracting(email -> email.getAllRecipients()[0].toString())
                .containsExactlyInAnyOrder("after-broken@example.com", "after-incomplete@example.com");
        // ... and the two bad messages are kept, unchanged, for inspection.
        assertThat(readDeadLetters(brokenKey, PATIENCE)).singleElement()
                .satisfies(deadLetter -> assertThat(deadLetter.value()).isEqualTo("this is not json"));
        assertThat(readDeadLetters(incompleteKey, PATIENCE)).hasSize(1);
    }

    @DisplayName("If the mail server stays down, the event is retried, then parked in the dead-letter topic, and not recorded as handled")
    @Test
    void parksEventWhenEmailCannotBeSent() throws Exception
    {
        UUID bookingId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        String undelivered = event(eventId, "BookingConfirmed", bookingId, "anna@example.com");

        mailServer.stop();
        try {
            publish(bookingId, undelivered);

            assertThat(readDeadLetters(bookingId.toString(), PATIENCE)).singleElement()
                    .satisfies(deadLetter -> assertThat(deadLetter.value()).isEqualTo(undelivered));
            // Not marked as handled, so replaying it from the dead-letter topic later will send the email.
            assertThat(redis.hasKey("notifications:processed:" + eventId)).isFalse();
        }
        finally {
            mailServer.start();
        }

        // Once the mail server is back, the service carries on with the next event of that partition.
        publishMarkerAfter(bookingId.toString(), "marker@example.com");
        assertThat(awaitEmails(1).get(0).getAllRecipients()[0]).hasToString("marker@example.com");
    }

    @DisplayName("The email carries the trace id of the booking request its event came from")
    @Test
    void emailCarriesTheTraceOfTheBooking() throws Exception
    {
        UUID bookingId = UUID.randomUUID();
        String traceId = "4bf92f3577b34da6a3ce929d0e0e4736";
        ProducerRecord<String, String> message = new ProducerRecord<>(TOPIC, bookingId.toString(),
                event(UUID.randomUUID(), "BookingConfirmed", bookingId, "anna@example.com"));
        // The header the booking API's outbox relay puts on every message.
        message.headers().add("traceparent", ("00-" + traceId + "-00f067aa0ba902b7-01").getBytes(StandardCharsets.UTF_8));

        // A plain producer: the template would start a trace of its own and replace the header.
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProperties())) {
            producer.send(message).get();
        }

        MimeMessage email = awaitEmails(1).get(0);
        assertThat(email.getHeader(Mail.TRACE_HEADER)).containsExactly(traceId);
    }

    // ------------------------------------------------------------ account events

    @DisplayName("A password reset event produces an email with the link and the moment it stops working")
    @Test
    void sendsThePasswordResetLink() throws Exception
    {
        String link = RESET_LINK + "k3Jd-9a_Zq";
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(30));

        publishAccountEvent(UUID.randomUUID(), "anna@example.com", link, expiresAt);

        MimeMessage email = awaitEmails(1).get(0);
        assertThat(email.getAllRecipients()[0]).hasToString("anna@example.com");
        assertThat(email.getSubject()).isEqualTo("Set a new password for Popcorn Palace");
        String until = DateTimeFormatter.ofPattern("HH:mm 'on' d MMMM", Locale.ENGLISH).format(expiresAt.atZone(ZoneOffset.UTC));
        assertThat(GreenMailUtil.getBody(email))
                .contains("Hi Anna,")
                // On a line of its own, whole: a mail program must be able to make it a link.
                .containsPattern("(?m)^" + Pattern.quote(link) + "\\s*$")
                .contains("The link works once, until " + until + " (UTC).")
                .contains("If it was not you, there is nothing to do");
    }

    @DisplayName("The same reset event delivered twice sends one email")
    @Test
    void ignoresARedeliveredResetEvent() throws Exception
    {
        UUID eventId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(30));

        publishAccountEvent(eventId, "anna@example.com", RESET_LINK + "first", expiresAt);
        publishAccountEvent(eventId, "anna@example.com", RESET_LINK + "first", expiresAt);
        // The topic has one partition, so this one is read after both of the above.
        publishAccountEvent(UUID.randomUUID(), "marker@example.com", RESET_LINK + "marker", expiresAt);

        assertThat(awaitEmails(2)).extracting(email -> email.getAllRecipients()[0].toString())
                .containsExactlyInAnyOrder("anna@example.com", "marker@example.com");
    }

    @DisplayName("A link that expired on its way here is not sent")
    @Test
    void doesNotSendAnExpiredLink() throws Exception
    {
        Instant soon = Instant.now().plus(Duration.ofMinutes(30));

        publishAccountEvent(UUID.randomUUID(), "late@example.com", RESET_LINK + "late", Instant.now().minusSeconds(1));
        publishAccountEvent(UUID.randomUUID(), "marker@example.com", RESET_LINK + "marker", soon);

        assertThat(awaitEmails(1).get(0).getAllRecipients()[0]).hasToString("marker@example.com");
    }

    @DisplayName("A reset event that cannot become an email is dropped, not parked: its link is in no dead-letter topic")
    @Test
    void dropsAResetEventInsteadOfParkingIt() throws Exception
    {
        Instant soon = Instant.now().plus(Duration.ofMinutes(30));
        UUID undelivered = UUID.randomUUID();
        double droppedBefore = dropped();

        mailServer.stop();
        try {
            publishAccountEvent(undelivered, "anna@example.com", RESET_LINK + "secret-token", soon);
            kafka.send(ACCOUNT_TOPIC, "broken", "this is not json, and says secret-token").get();

            // Retried, then given up on: both of them.
            await().atMost(PATIENCE).untilAsserted(() -> assertThat(dropped() - droppedBefore).isEqualTo(2));
        }
        finally {
            mailServer.start();
        }

        // Nothing was copied anywhere: not to a dead-letter topic of its own, not to the bookings' one.
        assertThat(readFrom(ACCOUNT_TOPIC + ".DLT", undelivered.toString(), Duration.ofSeconds(3))).isEmpty();
        assertThat(readFrom(ACCOUNT_TOPIC + ".DLT", "broken", Duration.ofSeconds(1))).isEmpty();
        assertThat(readDeadLetters(undelivered.toString(), Duration.ofSeconds(1))).isEmpty();
        // Not recorded as handled either, and the events behind it are read as usual.
        assertThat(redis.hasKey("notifications:processed:" + undelivered)).isFalse();
        publishAccountEvent(UUID.randomUUID(), "marker@example.com", RESET_LINK + "marker", soon);
        assertThat(awaitEmails(1).get(0).getAllRecipients()[0]).hasToString("marker@example.com");
    }

    @DisplayName("Health and metrics are served over HTTP, for the stack to watch")
    @Test
    void servesHealthAndMetrics() throws Exception
    {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();

        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.kafka.status").value("UP"))
                .andExpect(jsonPath("$.components.redis.status").value("UP"))
                .andExpect(jsonPath("$.components.mail.status").value("UP"));
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());

        String metrics = mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(metrics)
                .contains("application=\"popcorn-palace-notifications\"")
                .contains("popcorn_notifications_emails_total{")
                .contains("popcorn_notifications_dead_letters_total")
                .contains("popcorn_notifications_dropped_total")
                // The Kafka client's own metrics, labelled like everything else.
                .contains("kafka_consumer_connection_count{application=\"popcorn-palace-notifications\",");
    }

    // ------------------------------------------------------------ helpers

    private Properties producerProperties()
    {
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        return properties;
    }

    /** An account event as the booking API publishes it, keyed by account like the real ones. */
    private void publishAccountEvent(UUID eventId, String email, String link, Instant expiresAt) throws Exception
    {
        UUID accountId = UUID.randomUUID();
        kafka.send(ACCOUNT_TOPIC, eventId.toString(), """
                {
                  "eventId": "%s",
                  "type": "PasswordResetRequested",
                  "version": 1,
                  "occurredAt": "%s",
                  "account": {"id": "%s", "email": "%s", "displayName": "Anna"},
                  "passwordReset": {"url": "%s", "expiresAt": "%s"},
                  "somethingAddedLater": {"ignored": true}
                }
                """.formatted(eventId, Instant.now(), accountId, email, link, expiresAt)).get();
    }

    private double dropped()
    {
        return meters.get("popcorn.notifications.dropped").counter().count();
    }

    private void publish(UUID bookingId, String eventJson) throws Exception
    {
        kafka.send(TOPIC, bookingId.toString(), eventJson).get();
    }

    /**
     * Publishes one more valid event with the given Kafka key. Messages with the
     * same key go to the same partition and are consumed in order, so when this
     * event's email arrives, everything published before it under that key has
     * been dealt with.
     */
    private void publishMarkerAfter(String sameKey, String email) throws Exception
    {
        kafka.send(TOPIC, sameKey, event(UUID.randomUUID(), "BookingConfirmed", UUID.randomUUID(), email)).get();
    }

    /** A booking event as the booking API publishes it, including fields this service does not use. */
    private static String event(UUID eventId, String type, UUID bookingId, String email)
    {
        return """
                {
                  "eventId": "%s",
                  "type": "%s",
                  "version": 1,
                  "occurredAt": "2030-01-10T12:03:00Z",
                  "booking": {
                    "id": "%s",
                    "status": "CONFIRMED",
                    "showtimeId": 1,
                    "movieTitle": "Interstellar",
                    "hallName": "Hall 1",
                    "startTime": "2030-01-15T18:00:00Z",
                    "seats": [
                      {"row": 9, "seatNumber": 4, "category": "VIP", "price": 22.00},
                      {"row": 9, "seatNumber": 5, "category": "VIP", "price": 22.00}
                    ],
                    "totalPrice": 44.00,
                    "currency": "USD"
                  },
                  "customer": {"id": "%s", "email": "%s", "displayName": "Anna"},
                  "somethingAddedLater": {"ignored": true}
                }
                """.formatted(eventId, type, bookingId, UUID.randomUUID(), email);
    }

    private List<MimeMessage> awaitEmails(int expected)
    {
        await().atMost(PATIENCE).untilAsserted(() -> assertThat(mailServer.getReceivedMessages()).hasSize(expected));
        return List.of(mailServer.getReceivedMessages());
    }

    /** Reads the dead-letter topic from the start and returns the messages with the given key. */
    private List<ConsumerRecord<String, String>> readDeadLetters(String key, Duration timeout)
    {
        return readFrom(DEAD_LETTER_TOPIC, key, timeout);
    }

    private List<ConsumerRecord<String, String>> readFrom(String topic, String key, Duration timeout)
    {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        List<ConsumerRecord<String, String>> found = new ArrayList<>();
        long deadline = System.nanoTime() + timeout.toNanos();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties)) {
            consumer.subscribe(List.of(topic));
            while (found.isEmpty() && System.nanoTime() < deadline) {
                for (ConsumerRecord<String, String> message : consumer.poll(Duration.ofMillis(250))) {
                    if (key.equals(message.key())) {
                        found.add(message);
                    }
                }
            }
        }
        return found;
    }
}
