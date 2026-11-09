package com.antoniosifov.popcornpalace.hold;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A hold as shown to its owner.
 *
 * @param expiresAt when the seats are released unless the hold is confirmed first
 */
public record HoldResponse(
        UUID id,
        Long showtimeId,
        List<HoldRecord.HeldSeat> seats,
        BigDecimal totalPrice,
        Instant expiresAt
)
{
    public static HoldResponse from(HoldRecord hold)
    {
        return new HoldResponse(hold.id(), hold.showtimeId(), hold.seats(), hold.totalPrice(), hold.expiresAt());
    }
}
