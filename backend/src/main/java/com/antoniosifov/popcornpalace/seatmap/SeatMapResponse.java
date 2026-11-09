package com.antoniosifov.popcornpalace.seatmap;

import com.antoniosifov.popcornpalace.hall.SeatCategory;
import com.antoniosifov.popcornpalace.hall.SeatStatus;

import java.math.BigDecimal;
import java.util.List;

/**
 * Everything a client needs to draw the hall for one showtime and let the
 * customer pick seats: every seat, row by row, with its price and status.
 *
 * @param availableSeats seats that are neither booked nor held by anyone
 */
public record SeatMapResponse(
        Long showtimeId,
        Long hallId,
        String hallName,
        int totalSeats,
        int availableSeats,
        List<Row> rows
)
{
    public record Row(int row, List<SeatView> seats)
    {
    }

    /**
     * @param heldByMe true when the seat is held by the caller, so a client can
     *                 show "your selection" differently from seats taken by others
     */
    public record SeatView(Long seatId, int number, SeatCategory category, BigDecimal price,
                           SeatStatus status, boolean heldByMe)
    {
    }
}
