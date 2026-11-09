package com.antoniosifov.popcornpalace.booking;

import com.antoniosifov.popcornpalace.hall.Seat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** One sold seat of a booking, with the price it was sold for. */
@Entity
@Table(name = "booking_seats")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // required by JPA, not for application code
public class BookingSeat
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false, updatable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seat_id", nullable = false, updatable = false)
    private Seat seat;

    // Copies of the booking's showtime and hall. They exist for the database:
    // the unique constraint (showtime_id, seat_id) is what prevents selling a
    // seat twice, and the composite foreign keys tie seat, showtime and hall together.

    @Column(name = "showtime_id", nullable = false, updatable = false)
    private Long showtimeId;

    @Column(name = "hall_id", nullable = false, updatable = false)
    private Long hallId;

    /** A copy, so later price changes do not rewrite what the customer paid. */
    @Column(nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal price;

    BookingSeat(Booking booking, Seat seat, BigDecimal price)
    {
        this.booking = booking;
        this.seat = seat;
        this.showtimeId = booking.getShowtime().getId();
        this.hallId = booking.getHallId();
        this.price = price;
    }
}
