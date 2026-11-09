package com.antoniosifov.popcornpalace.booking;

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

    @Column(name = "seat_number", nullable = false, updatable = false)
    private int seatNumber;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Booking(Showtime showtime, int seatNumber, UUID userId, Instant createdAt)
    {
        this.showtime = showtime;
        this.seatNumber = seatNumber;
        this.userId = userId;
        this.createdAt = createdAt;
    }
}
