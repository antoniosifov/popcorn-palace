package com.antoniosifov.popcornpalace.booking;

import com.antoniosifov.popcornpalace.hall.Seat;
import com.antoniosifov.popcornpalace.hall.SeatCategory;
import com.antoniosifov.popcornpalace.showtime.Showtime;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A ticket: what was booked, where the seat is, when the showtime starts and what was paid. */
public record BookingResponse(
        UUID id,
        Long showtimeId,
        String movieTitle,
        String hallName,
        Instant startTime,
        Long seatId,
        int row,
        int seatNumber,
        SeatCategory category,
        BigDecimal price,
        UUID userId,
        Instant createdAt
)
{
    /** Reads the seat, showtime, movie and hall, so call it while they are loaded (inside a transaction). */
    public static BookingResponse from(Booking booking)
    {
        Seat seat = booking.getSeat();
        Showtime showtime = booking.getShowtime();
        return new BookingResponse(
                booking.getId(),
                showtime.getId(),
                showtime.getMovie().getTitle(),
                showtime.getHall().getName(),
                showtime.getStartTime(),
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
