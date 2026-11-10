package com.antoniosifov.popcornpalace.movie;

import com.antoniosifov.popcornpalace.common.text.Text;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Body of the create and update movie requests. The first five fields are the
 * facts every movie needs; the rest is how it is presented, and may be left out.
 */
public record MovieRequest(
        @Schema(example = "Interstellar")
        @NotBlank @Size(max = 200)
        String title,

        @Schema(example = "Science Fiction")
        @NotBlank @Size(max = 100)
        String genre,

        @Schema(example = "169")
        @NotNull @Positive @Max(900)
        Integer durationMinutes,

        @Schema(example = "8.7", description = "0.0 to 10.0, one decimal place")
        @NotNull @DecimalMin("0.0") @DecimalMax("10.0") @Digits(integer = 2, fraction = 1)
        BigDecimal rating,

        @Schema(example = "2014")
        @NotNull @Min(1888) @Max(2100)
        Integer releaseYear,

        @Schema(example = "A team of explorers travels through a wormhole in search of a new home for humanity.")
        @Size(max = 4000)
        String overview,

        @Schema(example = "Mankind was born on Earth. It was never meant to die here.")
        @Size(max = 300)
        String tagline,

        @Schema(example = "https://image.tmdb.org/t/p/w500/gEU2QniE6E77NI6lCU6MxlNBvIx.jpg", description = "An https address")
        @Size(max = 500) @Pattern(regexp = HTTPS_URL, message = "must be an https address")
        String posterUrl,

        @Schema(description = "A wide still of the movie; an https address")
        @Size(max = 500) @Pattern(regexp = HTTPS_URL, message = "must be an https address")
        String backdropUrl,

        @Schema(example = "https://www.youtube.com/watch?v=zSWdZVtXT7E", description = "An https address")
        @Size(max = 500) @Pattern(regexp = HTTPS_URL, message = "must be an https address")
        String trailerUrl,

        @Schema(example = "Christopher Nolan")
        @Size(max = 200)
        String director,

        @Schema(example = "[\"Matthew McConaughey\", \"Anne Hathaway\"]", description = "The leading cast, most prominent first")
        @Size(max = 8)
        List<@NotBlank @Size(max = 70) String> starring
)
{
    /**
     * These addresses end up in the page as the source of a picture and the
     * target of a link, so nothing but https is accepted: no javascript:, no data:.
     */
    private static final String HTTPS_URL = "https://[^\\s\"'<>]+";

    /** Text is normalized before validation, so a whitespace-only title is rejected as blank. */
    public MovieRequest
    {
        title = Text.normalize(title);
        genre = Text.normalize(genre);
        overview = blankToNull(Text.normalize(overview));
        tagline = blankToNull(Text.normalize(tagline));
        posterUrl = blankToNull(posterUrl);
        backdropUrl = blankToNull(backdropUrl);
        trailerUrl = blankToNull(trailerUrl);
        director = blankToNull(Text.normalize(director));
        starring = starring == null ? List.of()
                : starring.stream().filter(Objects::nonNull).map(Text::normalize).toList();
    }

    /** Only the facts; for a movie that has nothing else to show yet. */
    public MovieRequest(String title, String genre, Integer durationMinutes, BigDecimal rating, Integer releaseYear)
    {
        this(title, genre, durationMinutes, rating, releaseYear, null, null, null, null, null, null, null);
    }

    public MoviePresentation presentation()
    {
        return new MoviePresentation(overview, tagline, posterUrl, backdropUrl, trailerUrl, director, starring);
    }

    /** A field somebody cleared in a form arrives as an empty string; that is the same as leaving it out. */
    private static String blankToNull(String value)
    {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
