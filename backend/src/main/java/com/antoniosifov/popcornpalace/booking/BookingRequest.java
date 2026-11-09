package com.antoniosifov.popcornpalace.booking;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Body of the create booking request. It does not say who is booking:
 * that comes from the access token, so one user cannot book as another.
 */
public record BookingRequest(
        @Schema(example = "1")
        @NotNull @Positive
        Long showtimeId,

        @Schema(example = "42", description = "A seat id from the showtime's seat map")
        @NotNull @Positive
        Long seatId
)
{
}
