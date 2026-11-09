package com.antoniosifov.popcornpalace.hall;

/** A hall as shown in lists, without its row-by-row layout. */
public record HallSummaryResponse(Long id, String name, int rowCount, int seatCount)
{
    public static HallSummaryResponse from(Hall hall)
    {
        return new HallSummaryResponse(hall.getId(), hall.getName(), hall.getRowCount(), hall.getSeatCount());
    }
}
