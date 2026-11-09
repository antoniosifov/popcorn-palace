package com.antoniosifov.popcornpalace.booking;

import com.antoniosifov.popcornpalace.hall.Seat;
import com.antoniosifov.popcornpalace.showtime.Showtime;
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
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bookings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // required by JPA, not for application code
public class Booking
{
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "showtime_id", nullable = false, updatable = false)
    private Showtime showtime;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seat_id", nullable = false, updatable = false)
    private Seat seat;

    /**
     * The hall of both the showtime and the seat. It is stored on the booking
     * so the database can check, with two composite foreign keys, that the seat
     * really belongs to the hall the showtime runs in.
     */
    @Column(name = "hall_id", nullable = false, updatable = false)
    private Long hallId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /** What the customer paid: a copy, so later price changes do not rewrite history. */
    @Column(nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Booking(Showtime showtime, Seat seat, UUID userId, BigDecimal price, Instant createdAt)
    {
        this.showtime = showtime;
        this.seat = seat;
        this.hallId = showtime.getHall().getId();
        this.userId = userId;
        this.price = price;
        this.createdAt = createdAt;
    }
}
