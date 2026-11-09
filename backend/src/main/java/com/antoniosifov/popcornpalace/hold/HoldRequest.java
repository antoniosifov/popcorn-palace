package com.antoniosifov.popcornpalace.hold;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Body of the create hold request: the seats the customer picked on the seat map. */
public record HoldRequest(
        @Schema(example = "[101, 102]", description = "Seat ids from the showtime's seat map")
        @NotEmpty @Size(max = 50)
        List<@NotNull @Positive Long> seatIds
)
{
}
