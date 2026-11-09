package com.antoniosifov.popcornpalace.hold;

import com.antoniosifov.popcornpalace.hall.SeatCategory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A hold as it is stored in Redis (serialized to JSON): which seats one
 * customer has reserved for one showtime, at what prices, and until when.
 * The prices are fixed here, so the customer pays what they were shown even
 * if an administrator changes the showtime's prices in the meantime.
 */
public record HoldRecord(
        UUID id,
        Long showtimeId,
        UUID userId,
        List<HeldSeat> seats,
        BigDecimal totalPrice,
        Instant createdAt,
        Instant expiresAt
)
{
    public record HeldSeat(Long seatId, int row, int seatNumber, SeatCategory category, BigDecimal price)
    {
    }

    public List<Long> seatIds()
    {
        return seats.stream().map(HeldSeat::seatId).toList();
    }
}
