package com.antoniosifov.popcornpalace.booking;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

/** Body of the create booking request. */
public record BookingRequest(
        @Schema(example = "1")
        @NotNull @Positive
        Long showtimeId,

        @Schema(example = "15")
        @NotNull @Min(1) @Max(300)
        Integer seatNumber,

        @Schema(example = "84438967-f68f-4fa0-b620-0f08217e76af")
        @NotNull
        UUID userId
)
{
}
