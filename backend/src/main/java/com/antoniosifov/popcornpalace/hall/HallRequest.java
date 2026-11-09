package com.antoniosifov.popcornpalace.hall;

import com.antoniosifov.popcornpalace.common.text.Text;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Body of the create hall request. The layout is described as blocks of
 * identical rows, listed from the screen to the back of the hall.
 */
public record HallRequest(
        @Schema(example = "Hall 1")
        @NotBlank @Size(max = 100)
        String name,

        @Schema(description = "Blocks of identical rows, from the screen to the back")
        @NotEmpty @Size(max = 20)
        List<@Valid Section> sections
)
{
    public HallRequest
    {
        name = Text.normalize(name);
    }

    public record Section(
            @Schema(example = "8", description = "How many rows this block has")
            @NotNull @Min(1) @Max(50)
            Integer rows,

            @Schema(example = "12")
            @NotNull @Min(1) @Max(50)
            Integer seatsPerRow,

            @Schema(example = "STANDARD")
            @NotNull
            SeatCategory category
    )
    {
    }
}
