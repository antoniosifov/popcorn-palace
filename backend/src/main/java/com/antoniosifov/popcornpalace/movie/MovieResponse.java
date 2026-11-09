package com.antoniosifov.popcornpalace.movie;

import java.math.BigDecimal;

public record MovieResponse(
        Long id,
        String title,
        String genre,
        int durationMinutes,
        BigDecimal rating,
        int releaseYear
)
{
    public static MovieResponse from(Movie movie)
    {
        return new MovieResponse(
                movie.getId(),
                movie.getTitle(),
                movie.getGenre(),
                movie.getDurationMinutes(),
                movie.getRating(),
                movie.getReleaseYear()
        );
    }
}
