package com.antoniosifov.popcornpalace.booking.event;

import com.antoniosifov.popcornpalace.booking.Booking;
import com.antoniosifov.popcornpalace.booking.event.BookingEvent.BookingData;
import com.antoniosifov.popcornpalace.booking.event.BookingEvent.Customer;
import com.antoniosifov.popcornpalace.booking.event.BookingEvent.SeatLine;
import com.antoniosifov.popcornpalace.outbox.Outbox;
import com.antoniosifov.popcornpalace.showtime.Showtime;
import com.antoniosifov.popcornpalace.user.User;
import com.antoniosifov.popcornpalace.user.UserRepository;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Announces what happened to a booking. It does not talk to Kafka: it writes
 * the event to the outbox, inside the transaction that changed the booking,
 * and the outbox relay delivers it afterwards.
 */
@Component
public class BookingEventPublisher
{
    private final Outbox outbox;
    private final UserRepository userRepository;
    private final Clock clock;
    private final String topic;

    public BookingEventPublisher(Outbox outbox, UserRepository userRepository, Clock clock,
                                 @Value("${app.events.booking-topic}") String topic)
    {
        this.outbox = outbox;
        this.userRepository = userRepository;
        this.clock = clock;
        this.topic = topic;
    }

    /** Call inside the transaction that saves the booking. */
    public void confirmed(Booking booking)
    {
        publish(BookingEvent.CONFIRMED, booking);
    }

    /** Call inside the transaction that cancels the booking. */
    public void cancelled(Booking booking)
    {
        publish(BookingEvent.CANCELLED, booking);
    }

    private void publish(String type, Booking booking)
    {
        User user = userRepository.findById(booking.getUserId())
                .orElseThrow(() -> new IllegalStateException("Booking %s has no user".formatted(booking.getId())));
        Showtime showtime = booking.getShowtime();

        List<SeatLine> seats = booking.getSeats().stream()
                .map(seat -> new SeatLine(seat.getSeat().getRowNumber(), seat.getSeat().getSeatNumber(),
                        seat.getSeat().getCategory().name(), seat.getPrice()))
                .sorted(Comparator.comparingInt(SeatLine::row).thenComparingInt(SeatLine::seatNumber))
                .toList();

        UUID eventId = UUID.randomUUID();
        BookingEvent event = new BookingEvent(
                eventId,
                type,
                BookingEvent.CURRENT_VERSION,
                clock.instant().truncatedTo(ChronoUnit.MILLIS),
                new BookingData(
                        booking.getId(),
                        booking.getStatus().name(),
                        showtime.getId(),
                        showtime.getMovie().getTitle(),
                        showtime.getHall().getName(),
                        showtime.getStartTime(),
                        seats,
                        booking.getTotalPrice(),
                        booking.getPayment() == null ? null : booking.getPayment().getCurrency()),
                new Customer(user.getId(), user.getEmail(), user.getDisplayName()));

        // Keyed by booking id: Kafka keeps one booking's events in order.
        outbox.append(topic, booking.getId().toString(), type, eventId, event);
    }

    @Configuration
    static class TopicConfig
    {
        /** Created at startup if it does not exist. One replica is enough for a single-broker setup. */
        @Bean
        NewTopic bookingEventsTopic(@Value("${app.events.booking-topic}") String topic)
        {
            return TopicBuilder.name(topic).partitions(3).replicas(1).build();
        }
    }
}
