package com.antoniosifov.popcornpalace.showtime;

import com.antoniosifov.popcornpalace.hall.SeatCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/** Body of the create and update showtime requests. */
@EndAfterStart
public record ShowtimeRequest(
        @Schema(example = "1")
        @NotNull @Positive
        Long movieId,

        @Schema(example = "1")
        @NotNull @Positive
        Long hallId,

        @Schema(example = "2030-01-15T18:00:00Z", description = "ISO-8601 instant with an offset or Z")
        @NotNull
        Instant startTime,

        @Schema(example = "2030-01-15T20:49:00Z", description = "Must be after startTime")
        @NotNull
        Instant endTime,

        @Schema(example = "{\"STANDARD\": 14.50, \"VIP\": 22.00}",
                description = "Ticket price per seat category. Every category used in the hall needs a price.")
        @NotEmpty
        Map<SeatCategory, @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal> prices
)
{
}
