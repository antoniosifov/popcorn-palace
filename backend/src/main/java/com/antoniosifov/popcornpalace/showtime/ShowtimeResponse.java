package com.antoniosifov.popcornpalace.showtime;

import java.math.BigDecimal;
import java.time.Instant;

public record ShowtimeResponse(
        Long id,
        Long movieId,
        String movieTitle,
        String theater,
        Instant startTime,
        Instant endTime,
        BigDecimal price
)
{
    /** Reads the movie, so call it while the showtime's movie is loaded (inside a transaction). */
    public static ShowtimeResponse from(Showtime showtime)
    {
        return new ShowtimeResponse(
                showtime.getId(),
                showtime.getMovie().getId(),
                showtime.getMovie().getTitle(),
                showtime.getTheater(),
                showtime.getStartTime(),
                showtime.getEndTime(),
                showtime.getPrice()
        );
    }
}
