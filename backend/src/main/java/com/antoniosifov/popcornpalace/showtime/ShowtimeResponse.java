package com.antoniosifov.popcornpalace.showtime;

import com.antoniosifov.popcornpalace.hall.SeatCategory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

public record ShowtimeResponse(
        Long id,
        Long movieId,
        String movieTitle,
        Long hallId,
        String hallName,
        Instant startTime,
        Instant endTime,
        Map<SeatCategory, BigDecimal> prices
)
{
    /** Reads the movie, hall and prices, so call it inside a transaction. */
    public static ShowtimeResponse from(Showtime showtime)
    {
        return new ShowtimeResponse(
                showtime.getId(),
                showtime.getMovie().getId(),
                showtime.getMovie().getTitle(),
                showtime.getHall().getId(),
                showtime.getHall().getName(),
                showtime.getStartTime(),
                showtime.getEndTime(),
                // A copy in enum order: detaches the response from the entity's collection.
                new EnumMap<>(showtime.getPrices())
        );
    }
}
