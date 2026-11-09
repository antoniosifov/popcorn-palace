package com.antoniosifov.popcornpalace.booking;

import com.antoniosifov.popcornpalace.hall.Seat;
import com.antoniosifov.popcornpalace.hall.SeatCategory;
import com.antoniosifov.popcornpalace.showtime.Showtime;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** An order with its tickets: the showtime, each seat with the price paid, and the total. */
public record BookingResponse(
        UUID id,
        Long showtimeId,
        String movieTitle,
        String hallName,
        Instant startTime,
        List<Ticket> seats,
        BigDecimal totalPrice,
        UUID userId,
        Instant createdAt
)
{
    public record Ticket(Long seatId, int row, int seatNumber, SeatCategory category, BigDecimal price)
    {
    }

    /** Reads the seats, showtime, movie and hall, so call it while they can be loaded (inside a transaction). */
    public static BookingResponse from(Booking booking)
    {
        Showtime showtime = booking.getShowtime();
        List<Ticket> tickets = booking.getSeats().stream()
                .map(BookingResponse::ticket)
                .sorted(Comparator.comparingInt(Ticket::row).thenComparingInt(Ticket::seatNumber))
                .toList();
        return new BookingResponse(
                booking.getId(),
                showtime.getId(),
                showtime.getMovie().getTitle(),
                showtime.getHall().getName(),
                showtime.getStartTime(),
                tickets,
                booking.getTotalPrice(),
                booking.getUserId(),
                booking.getCreatedAt()
        );
    }

    private static Ticket ticket(BookingSeat bookingSeat)
    {
        Seat seat = bookingSeat.getSeat();
        return new Ticket(seat.getId(), seat.getRowNumber(), seat.getSeatNumber(), seat.getCategory(), bookingSeat.getPrice());
    }
}
