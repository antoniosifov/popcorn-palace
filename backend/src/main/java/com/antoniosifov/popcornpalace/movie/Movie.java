package com.antoniosifov.popcornpalace.movie;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

@Entity
@Table(name = "movies")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // required by JPA, not for application code
public class Movie
{
    /** Cast names are kept in one column, one name per line. */
    private static final String NAME_SEPARATOR = "\n";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 100)
    private String genre;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(nullable = false, precision = 3, scale = 1)
    private BigDecimal rating;

    @Column(name = "release_year", nullable = false)
    private int releaseYear;

    @Column(length = 4000)
    private String overview;

    @Column(length = 300)
    private String tagline;

    @Column(name = "poster_url", length = 500)
    private String posterUrl;

    @Column(name = "backdrop_url", length = 500)
    private String backdropUrl;

    @Column(name = "trailer_url", length = 500)
    private String trailerUrl;

    @Column(length = 200)
    private String director;

    @Getter(AccessLevel.NONE)
    @Column(length = 600)
    private String starring;

    /** The movie's id at TMDB, when it was imported from there; null for a movie added by hand. */
    @Column(name = "tmdb_id")
    private Long tmdbId;

    /** How much attention the movie gets at its source; decides what the programme leads with. */
    private Double popularity;

    /** Whether the latest import found the movie playing in cinemas. */
    @Column(name = "in_cinemas", nullable = false)
    private boolean inCinemas;

    public Movie(String title, String genre, int durationMinutes, BigDecimal rating, int releaseYear,
                 MoviePresentation presentation)
    {
        update(title, genre, durationMinutes, rating, releaseYear, presentation);
    }

    public void update(String title, String genre, int durationMinutes, BigDecimal rating, int releaseYear,
                       MoviePresentation presentation)
    {
        this.title = title;
        this.genre = genre;
        this.durationMinutes = durationMinutes;
        this.rating = rating;
        this.releaseYear = releaseYear;
        this.overview = presentation.overview();
        this.tagline = presentation.tagline();
        this.posterUrl = presentation.posterUrl();
        this.backdropUrl = presentation.backdropUrl();
        this.trailerUrl = presentation.trailerUrl();
        this.director = presentation.director();
        this.starring = presentation.starring().isEmpty() ? null : String.join(NAME_SEPARATOR, presentation.starring());
    }

    /** Records where an imported movie came from and how it stands there. */
    public void importedFrom(long tmdbId, Double popularity)
    {
        this.tmdbId = tmdbId;
        this.popularity = popularity;
        this.inCinemas = true;
    }

    /** The movie is no longer playing: it keeps its page and its bookings, and gets no new showtimes. */
    public void leaveCinemas()
    {
        this.inCinemas = false;
    }

    public List<String> getStarring()
    {
        return starring == null ? List.of() : Arrays.asList(starring.split(NAME_SEPARATOR));
    }

    public MoviePresentation getPresentation()
    {
        return new MoviePresentation(overview, tagline, posterUrl, backdropUrl, trailerUrl, director, getStarring());
    }
}
