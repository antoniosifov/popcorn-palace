package com.antoniosifov.popcornpalace.seatmap;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/showtimes/{showtimeId}/seats")
@Tag(name = "Checkout")
public class SeatMapController
{
    private final SeatMapService seatMapService;

    public SeatMapController(SeatMapService seatMapService)
    {
        this.seatMapService = seatMapService;
    }

    @GetMapping
    @Operation(summary = "Seat map of a showtime", description = """
            Every seat of the hall, row by row, with its price for this showtime and its status: \
            AVAILABLE, HELD (someone is checking out) or BOOKED. Public; when called with an access \
            token, the caller's own held seats are marked with heldByMe.""")
    public SeatMapResponse get(@PathVariable Long showtimeId, @AuthenticationPrincipal Jwt jwt)
    {
        // The endpoint is public, so there may be no token at all.
        UUID viewerId = jwt == null ? null : UUID.fromString(jwt.getSubject());
        return seatMapService.forShowtime(showtimeId, viewerId);
    }
}
