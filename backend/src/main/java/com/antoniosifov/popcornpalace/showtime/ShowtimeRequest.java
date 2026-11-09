package com.antoniosifov.popcornpalace.showtime;

import com.antoniosifov.popcornpalace.common.text.Text;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/** Body of the create and update showtime requests. */
@EndAfterStart
public record ShowtimeRequest(
        @Schema(example = "1")
        @NotNull @Positive
        Long movieId,

        @Schema(example = "Hall 1")
        @NotBlank @Size(max = 100)
        String theater,

        @Schema(example = "2030-01-15T18:00:00Z", description = "ISO-8601 instant with an offset or Z")
        @NotNull
        Instant startTime,

        @Schema(example = "2030-01-15T20:49:00Z", description = "Must be after startTime")
        @NotNull
        Instant endTime,

        @Schema(example = "14.50")
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2)
        BigDecimal price
)
{
    public ShowtimeRequest
    {
        theater = Text.normalize(theater);
    }
}
