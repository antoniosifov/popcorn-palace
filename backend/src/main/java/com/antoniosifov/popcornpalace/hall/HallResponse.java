package com.antoniosifov.popcornpalace.hall;

import java.util.List;

/** A hall with its layout, one entry per row. */
public record HallResponse(Long id, String name, int rowCount, int seatCount, List<Row> rows)
{
    public record Row(int row, SeatCategory category, long seats)
    {
    }

    public static HallResponse from(Hall hall, List<Row> rows)
    {
        return new HallResponse(hall.getId(), hall.getName(), hall.getRowCount(), hall.getSeatCount(), rows);
    }
}
