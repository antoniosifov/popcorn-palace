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
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Body of the create and update movie requests. */
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
        Integer releaseYear
)
{
    /** Text is normalized before validation, so a whitespace-only title is rejected as blank. */
    public MovieRequest
    {
        title = Text.normalize(title);
        genre = Text.normalize(genre);
    }
}
