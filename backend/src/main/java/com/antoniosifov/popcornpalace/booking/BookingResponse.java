package com.antoniosifov.popcornpalace.booking;

import com.antoniosifov.popcornpalace.hall.Seat;
import com.antoniosifov.popcornpalace.hall.SeatCategory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BookingResponse(
        UUID id,
        Long showtimeId,
        Long seatId,
        int row,
        int seatNumber,
        SeatCategory category,
        BigDecimal price,
        UUID userId,
        Instant createdAt
)
{
    /** Reads the seat, so call it while the booking's seat is loaded (inside a transaction). */
    public static BookingResponse from(Booking booking)
    {
        Seat seat = booking.getSeat();
        return new BookingResponse(
                booking.getId(),
                // Reading only the id does not load the showtime from the database.
                booking.getShowtime().getId(),
                seat.getId(),
                seat.getRowNumber(),
                seat.getSeatNumber(),
                seat.getCategory(),
                booking.getPrice(),
                booking.getUserId(),
                booking.getCreatedAt()
        );
    }
}
