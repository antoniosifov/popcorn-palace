package com.antoniosifov.popcornpalace.booking;

import java.time.Instant;
import java.util.UUID;

public record BookingResponse(
        UUID id,
        Long showtimeId,
        int seatNumber,
        UUID userId,
        Instant createdAt
)
{
    public static BookingResponse from(Booking booking)
    {
        return new BookingResponse(
                booking.getId(),
                // Reading only the id does not load the showtime from the database.
                booking.getShowtime().getId(),
                booking.getSeatNumber(),
                booking.getUserId(),
                booking.getCreatedAt()
        );
    }
}
