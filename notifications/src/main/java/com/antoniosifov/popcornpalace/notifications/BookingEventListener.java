package com.antoniosifov.popcornpalace.notifications;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns booking events into emails.
 *
 * <p>Delivery is at-least-once, so the same event can arrive twice. The order
 * of the steps decides what happens if this service dies in the middle:
 * the email is sent first and the event is recorded as handled second. A
 * crash between the two means the event is redelivered and the customer may
 * get the email twice. The other order would risk the opposite, an event
 * recorded as handled whose email was never sent, and a missing ticket is
 * worse than a duplicate one.
 */
@Component
public class BookingEventListener
{
    private static final Logger log = LoggerFactory.getLogger(BookingEventListener.class);

    private final JsonMapper jsonMapper;
    private final ProcessedEvents processedEvents;
    private final TicketMailer mailer;

    public BookingEventListener(JsonMapper jsonMapper, ProcessedEvents processedEvents, TicketMailer mailer)
    {
        this.jsonMapper = jsonMapper;
        this.processedEvents = processedEvents;
        this.mailer = mailer;
    }

    /**
     * An exception thrown from here makes Kafka redeliver the message; after the
     * configured retries it is moved to the dead-letter topic (see KafkaErrorHandling).
     */
    @KafkaListener(topics = "${app.events.booking-topic}")
    public void onBookingEvent(ConsumerRecord<String, String> message)
    {
        BookingEvent event = parse(message);

        if (processedEvents.contains(event.eventId())) {
            log.info("Ignoring event {} ({}): already handled", event.eventId(), event.type());
            return;
        }

        switch (event.type()) {
            case BookingEvent.CONFIRMED -> mailer.sendTickets(event);
            case BookingEvent.CANCELLED -> mailer.sendCancellation(event);
            default -> {
                // A type this service does not know about yet. Not an error:
                // the API may publish new kinds of events at any time.
                log.debug("Ignoring event {} of unhandled type {}", event.eventId(), event.type());
                return;
            }
        }

        processedEvents.add(event.eventId());
        log.info("Sent {} email for booking {} (event {})", event.type(), event.booking().id(), event.eventId());
    }

    private BookingEvent parse(ConsumerRecord<String, String> message)
    {
        BookingEvent event;
        try {
            event = jsonMapper.readValue(message.value(), BookingEvent.class);
        }
        catch (JacksonException | IllegalArgumentException exception) {
            throw new InvalidEventException("Message at %s-%d@%d is not a booking event"
                    .formatted(message.topic(), message.partition(), message.offset()), exception);
        }
        if (event == null) {
            throw new InvalidEventException("Message at %s-%d@%d is empty"
                    .formatted(message.topic(), message.partition(), message.offset()));
        }
        event.validate();
        return event;
    }
}
