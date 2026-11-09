package com.antoniosifov.popcornpalace.booking.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * What is published to Kafka when a booking is confirmed or cancelled. This
 * record is a public contract: other services read it, so fields may be added
 * but existing ones must not be renamed or removed without a new {@link #version}.
 *
 * <p>The event carries everything a consumer needs (the customer's email, the
 * movie, the seats), so that a consumer never has to call back into this
 * application to do its job.
 *
 * @param eventId identifies this event across redeliveries; consumers use it to ignore duplicates
 * @param type    {@link #CONFIRMED} or {@link #CANCELLED}
 * @param version schema version of this payload
 */
public record BookingEvent(
        UUID eventId,
        String type,
        int version,
        Instant occurredAt,
        BookingData booking,
        Customer customer
)
{
    public static final String CONFIRMED = "BookingConfirmed";
    public static final String CANCELLED = "BookingCancelled";
    public static final int CURRENT_VERSION = 1;

    public record BookingData(
            UUID id,
            String status,
            Long showtimeId,
            String movieTitle,
            String hallName,
            Instant startTime,
            List<SeatLine> seats,
            BigDecimal totalPrice,
            String currency
    )
    {
    }

    public record SeatLine(int row, int seatNumber, String category, BigDecimal price)
    {
    }

    public record Customer(UUID id, String email, String displayName)
    {
    }
}
