package com.antoniosifov.popcornpalace.movie;

import java.math.BigDecimal;
import java.util.List;

/**
 * A movie as the API shows it.
 *
 * @param inCinemas  whether the movie is playing in cinemas right now, according to the latest import
 * @param popularity how much attention the movie gets at its source (higher is more); null for a movie added by hand
 */
public record MovieResponse(
        Long id,
        String title,
        String genre,
        int durationMinutes,
        BigDecimal rating,
        int releaseYear,
        String overview,
        String tagline,
        String posterUrl,
        String backdropUrl,
        String trailerUrl,
        String director,
        List<String> starring,
        boolean inCinemas,
        Double popularity
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
                movie.getReleaseYear(),
                movie.getOverview(),
                movie.getTagline(),
                movie.getPosterUrl(),
                movie.getBackdropUrl(),
                movie.getTrailerUrl(),
                movie.getDirector(),
                movie.getStarring(),
                movie.isInCinemas(),
                movie.getPopularity()
        );
    }
}
