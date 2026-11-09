package com.antoniosifov.popcornpalace.notifications;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
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
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The whole service, as it runs: a booking event is published to a real
 * Kafka, and the test waits for the email to arrive at a mail server.
 */
@SpringBootTest(properties = {
        "spring.mail.host=127.0.0.1",
        "spring.mail.port=3025",
        // Fail fast in tests: two quick retries before the dead-letter topic.
        "app.notifications.max-retries=2",
        "app.notifications.retry-interval=PT0.2S"
})
@Import(TestcontainersConfiguration.class)
class NotificationFlowTest
{
    private static final String TOPIC = "popcorn.booking-events";
    private static final String DEAD_LETTER_TOPIC = TOPIC + ".DLT";
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

    // ------------------------------------------------------------ helpers

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
            consumer.subscribe(List.of(DEAD_LETTER_TOPIC));
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
