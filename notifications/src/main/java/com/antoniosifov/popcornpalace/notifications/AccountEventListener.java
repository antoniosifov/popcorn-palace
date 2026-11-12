package com.antoniosifov.popcornpalace.notifications;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;

/**
 * Turns account events into emails: the link that sets a new password, and
 * the last message to an account that its owner has deleted.
 *
 * <p>The same order of steps as for bookings, for the same reason: the email
 * first, the event recorded as handled second, so a crash in between repeats
 * an email rather than losing it.
 *
 * <p>What differs is what the event carries. The link is a secret with a
 * short life, so nothing here writes it down: log lines name the event, never
 * its content, and an event that cannot be turned into an email is dropped
 * instead of parked in a dead-letter topic (see {@link KafkaErrorHandling}).
 * A link that has already expired by the time it gets here is not sent at all.
 * The farewell to a deleted account is treated the same way, because of whose
 * email it carries: somebody who has asked for it to be forgotten.
 */
@Component
public class AccountEventListener
{
    private static final Logger log = LoggerFactory.getLogger(AccountEventListener.class);

    private final JsonMapper jsonMapper;
    private final ProcessedEvents processedEvents;
    private final AccountMailer mailer;
    private final Clock clock;
    private final Counter resetEmails;
    private final Counter farewellEmails;
    private final Counter duplicates;

    public AccountEventListener(JsonMapper jsonMapper, ProcessedEvents processedEvents, AccountMailer mailer,
                                Clock clock, MeterRegistry registry)
    {
        this.jsonMapper = jsonMapper;
        this.processedEvents = processedEvents;
        this.mailer = mailer;
        this.clock = clock;
        resetEmails = emails(registry, AccountEvent.PASSWORD_RESET_REQUESTED);
        farewellEmails = emails(registry, AccountEvent.ACCOUNT_DELETED);
        duplicates = Counter.builder("popcorn.notifications.duplicates")
                .description("Events delivered again that had already been handled")
                .register(registry);
    }

    private static Counter emails(MeterRegistry registry, String type)
    {
        return Counter.builder("popcorn.notifications.emails")
                .description("Emails sent, by the event that caused them")
                .tag("type", type)
                .register(registry);
    }

    /** An exception thrown from here makes Kafka redeliver the message; after the configured retries it is dropped. */
    @KafkaListener(topics = "${app.events.account-topic}")
    public void onAccountEvent(ConsumerRecord<String, String> message)
    {
        AccountEvent event = parse(message);

        if (processedEvents.contains(event.eventId())) {
            duplicates.increment();
            log.info("Ignoring event {} ({}): already handled", event.eventId(), event.type());
            return;
        }

        switch (event.type()) {
            case AccountEvent.PASSWORD_RESET_REQUESTED -> sendResetLink(event);
            case AccountEvent.ACCOUNT_DELETED -> {
                mailer.sendFarewell(event);
                farewellEmails.increment();
                log.info("Sent {} email (event {})", event.type(), event.eventId());
            }
            default -> {
                // A type this service does not know about yet. Not an error.
                log.debug("Ignoring event {} of unhandled type {}", event.eventId(), event.type());
                return;
            }
        }
        processedEvents.add(event.eventId());
    }

    private void sendResetLink(AccountEvent event)
    {
        if (event.passwordReset().expiresAt().isBefore(clock.instant())) {
            // Held up for longer than the link lives (this service was down, say). An
            // email with a dead link helps nobody; the customer will have asked again.
            log.info("Not sending the link of event {}: it has expired on the way", event.eventId());
            return;
        }
        mailer.sendPasswordReset(event);
        resetEmails.increment();
        log.info("Sent {} email (event {})", event.type(), event.eventId());
    }

    private AccountEvent parse(ConsumerRecord<String, String> message)
    {
        AccountEvent event;
        try {
            event = jsonMapper.readValue(message.value(), AccountEvent.class);
        }
        catch (JacksonException | IllegalArgumentException exception) {
            // The cause is left out on purpose: a parser's message quotes what it could not read.
            throw new InvalidEventException("Message at %s-%d@%d is not an account event"
                    .formatted(message.topic(), message.partition(), message.offset()));
        }
        if (event == null) {
            throw new InvalidEventException("Message at %s-%d@%d is empty"
                    .formatted(message.topic(), message.partition(), message.offset()));
        }
        event.validate();
        return event;
    }
}
