package com.antoniosifov.popcornpalace.booking;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/showtimes/{showtimeId}/seats")
@Tag(name = "Bookings")
public class SeatMapController
{
    private final SeatMapService seatMapService;

    public SeatMapController(SeatMapService seatMapService)
    {
        this.seatMapService = seatMapService;
    }

    @GetMapping
    @Operation(summary = "Seat map of a showtime",
            description = "Every seat of the hall, row by row, with its price for this showtime and whether it is still available.")
    public SeatMapResponse get(@PathVariable Long showtimeId)
    {
        return seatMapService.forShowtime(showtimeId);
    }
}
