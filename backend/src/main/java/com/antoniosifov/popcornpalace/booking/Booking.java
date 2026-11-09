package com.antoniosifov.popcornpalace.booking;

import com.antoniosifov.popcornpalace.hall.Seat;
import com.antoniosifov.popcornpalace.payment.Payment;
import com.antoniosifov.popcornpalace.showtime.Showtime;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** An order: one customer, one showtime, one or more seats, one payment. */
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

    /**
     * The showtime's hall, repeated here so the database can tie the booking,
     * its seats and the showtime to the same hall with composite foreign keys.
     */
    @Column(name = "hall_id", nullable = false, updatable = false)
    private Long hallId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /** Null only for bookings made before payments existed. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", updatable = false)
    private Payment payment;

    /** Loaded in batches so listing a page of bookings does not run one query each. */
    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 100)
    private List<BookingSeat> seats = new ArrayList<>();

    /** Sum of the seat prices at the time of sale. */
    @Column(name = "total_price", nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    /**
     * Optimistic lock: if two requests cancel the same booking at once, the
     * second update fails instead of running the cancellation (and refund) twice.
     */
    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Booking(Showtime showtime, UUID userId, Payment payment, Instant createdAt)
    {
        this.showtime = showtime;
        this.hallId = showtime.getHall().getId();
        this.userId = userId;
        this.payment = payment;
        this.totalPrice = BigDecimal.ZERO;
        this.status = BookingStatus.CONFIRMED;
        this.createdAt = createdAt;
    }

    /** Adds a seat at the price it is sold for and keeps the total in step. */
    public void addSeat(Seat seat, BigDecimal price)
    {
        seats.add(new BookingSeat(this, seat, price));
        totalPrice = totalPrice.add(price);
    }

    public boolean isCancelled()
    {
        return status == BookingStatus.CANCELLED;
    }

    /** Marks the booking cancelled and puts its seats back on sale. The rows stay, as history. */
    public void cancel(Instant now)
    {
        if (isCancelled()) {
            return;
        }
        this.status = BookingStatus.CANCELLED;
        this.cancelledAt = now;
        seats.forEach(BookingSeat::release);
    }
}
