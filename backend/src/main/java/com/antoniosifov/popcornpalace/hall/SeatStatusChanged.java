package com.antoniosifov.popcornpalace.hall;

import java.util.List;
import java.util.UUID;

/**
 * Announces that some seats of a showtime changed status: they were held,
 * released, sold, or came back on sale. Published inside the application
 * (Spring's event bus) by whatever caused the change; the seat map stream
 * forwards it to every browser looking at that showtime.
 *
 * @param holdId the hold that now has the seats, when the status is HELD, so a
 *               client can tell its own selection from everybody else's
 */
public record SeatStatusChanged(Long showtimeId, List<Long> seatIds, SeatStatus status, UUID holdId)
{
    public static SeatStatusChanged held(Long showtimeId, List<Long> seatIds, UUID holdId)
    {
        return new SeatStatusChanged(showtimeId, List.copyOf(seatIds), SeatStatus.HELD, holdId);
    }

    public static SeatStatusChanged available(Long showtimeId, List<Long> seatIds)
    {
        return new SeatStatusChanged(showtimeId, List.copyOf(seatIds), SeatStatus.AVAILABLE, null);
    }

    public static SeatStatusChanged booked(Long showtimeId, List<Long> seatIds)
    {
        return new SeatStatusChanged(showtimeId, List.copyOf(seatIds), SeatStatus.BOOKED, null);
    }
}
