package com.antoniosifov.popcornpalace.notifications;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A booking event as this service reads it. This is deliberately its own
 * class, not one shared with the booking API: it lists only the fields this
 * service uses and ignores everything else, so the API can add fields to its
 * events without this service having to change or be redeployed.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BookingEvent(
        UUID eventId,
        String type,
        Instant occurredAt,
        Booking booking,
        Customer customer
)
{
    public static final String CONFIRMED = "BookingConfirmed";
    public static final String CANCELLED = "BookingCancelled";

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Booking(
            UUID id,
            String movieTitle,
            String hallName,
            Instant startTime,
            List<Seat> seats,
            BigDecimal totalPrice,
            String currency
    )
    {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Seat(int row, int seatNumber, String category, BigDecimal price)
    {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Customer(String email, String displayName)
    {
    }

    /**
     * Checks that the event has what an email needs.
     *
     * @throws InvalidEventException if something required is missing
     */
    public void validate()
    {
        require(eventId != null, "eventId");
        require(type != null && !type.isBlank(), "type");
        require(booking != null, "booking");
        require(booking.id() != null, "booking.id");
        require(booking.movieTitle() != null, "booking.movieTitle");
        require(booking.startTime() != null, "booking.startTime");
        require(booking.seats() != null && !booking.seats().isEmpty(), "booking.seats");
        require(customer != null, "customer");
        require(customer.email() != null && customer.email().contains("@"), "customer.email");
    }

    private static void require(boolean present, String field)
    {
        if (!present) {
            throw new InvalidEventException("Booking event is missing " + field);
        }
    }
}
