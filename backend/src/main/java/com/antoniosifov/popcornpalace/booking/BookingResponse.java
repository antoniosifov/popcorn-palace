package com.antoniosifov.popcornpalace.booking;

import com.antoniosifov.popcornpalace.hall.Seat;
import com.antoniosifov.popcornpalace.hall.SeatCategory;
import com.antoniosifov.popcornpalace.payment.Payment;
import com.antoniosifov.popcornpalace.payment.PaymentStatus;
import com.antoniosifov.popcornpalace.showtime.Showtime;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** An order with its tickets: the showtime, each seat with the price paid, the total and the payment. */
public record BookingResponse(
        UUID id,
        BookingStatus status,
        Long showtimeId,
        Long movieId,
        String movieTitle,
        String moviePosterUrl,
        String hallName,
        Instant startTime,
        List<Ticket> seats,
        BigDecimal totalPrice,
        PaymentSummary payment,
        UUID userId,
        Instant createdAt,
        Instant cancelledAt
)
{
    public record Ticket(Long seatId, int row, int seatNumber, SeatCategory category, BigDecimal price)
    {
    }

    /** @param status SUCCEEDED for a paid booking; REFUND_PENDING, then REFUNDED, after a cancellation */
    public record PaymentSummary(UUID id, PaymentStatus status, BigDecimal amount, String currency)
    {
    }

    /** Reads the seats, showtime, movie, hall and payment, so call it while they can be loaded (inside a transaction). */
    public static BookingResponse from(Booking booking)
    {
        Showtime showtime = booking.getShowtime();
        List<Ticket> tickets = booking.getSeats().stream()
                .map(BookingResponse::ticket)
                .sorted(Comparator.comparingInt(Ticket::row).thenComparingInt(Ticket::seatNumber))
                .toList();
        Payment payment = booking.getPayment();
        return new BookingResponse(
                booking.getId(),
                booking.getStatus(),
                showtime.getId(),
                showtime.getMovie().getId(),
                showtime.getMovie().getTitle(),
                showtime.getMovie().getPosterUrl(),
                showtime.getHall().getName(),
                showtime.getStartTime(),
                tickets,
                booking.getTotalPrice(),
                payment == null ? null
                        : new PaymentSummary(payment.getId(), payment.getStatus(), payment.getAmount(), payment.getCurrency()),
                booking.getUserId(),
                booking.getCreatedAt(),
                booking.getCancelledAt()
        );
    }

    private static Ticket ticket(BookingSeat bookingSeat)
    {
        Seat seat = bookingSeat.getSeat();
        return new Ticket(seat.getId(), seat.getRowNumber(), seat.getSeatNumber(), seat.getCategory(), bookingSeat.getPrice());
    }
}
