package com.antoniosifov.popcornpalace.booking;

import com.antoniosifov.popcornpalace.hall.SeatCategory;

import java.math.BigDecimal;
import java.util.List;

/**
 * Everything a client needs to draw the hall for one showtime and let the
 * customer pick a seat: every seat, row by row, with its price and whether
 * it is still free.
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

    public record SeatView(Long seatId, int number, SeatCategory category, BigDecimal price, SeatStatus status)
    {
    }

    public enum SeatStatus
    {
        AVAILABLE,
        BOOKED
    }
}
