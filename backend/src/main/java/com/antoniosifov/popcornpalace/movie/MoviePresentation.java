package com.antoniosifov.popcornpalace.movie;

import java.util.List;

/**
 * What a customer sees of a movie besides its facts: a description, the
 * artwork, a trailer, who made it. All of it is optional; a movie without a
 * poster is still a movie that can be booked.
 *
 * @param starring the leading cast, most prominent first
 */
public record MoviePresentation(
        String overview,
        String tagline,
        String posterUrl,
        String backdropUrl,
        String trailerUrl,
        String director,
        List<String> starring
)
{
    public static final MoviePresentation NONE = new MoviePresentation(null, null, null, null, null, null, List.of());

    public MoviePresentation
    {
        starring = starring == null ? List.of() : List.copyOf(starring);
    }
}
