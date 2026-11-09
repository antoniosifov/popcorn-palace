package com.antoniosifov.popcornpalace.seatmap;

import com.antoniosifov.popcornpalace.common.error.ApiException;
import com.antoniosifov.popcornpalace.common.error.ErrorCode;
import com.antoniosifov.popcornpalace.showtime.ShowtimeRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/showtimes/{showtimeId}/seats/stream")
@Tag(name = "Checkout")
public class SeatMapStreamController
{
    private final SeatMapBroadcaster broadcaster;
    private final ShowtimeRepository showtimeRepository;

    public SeatMapStreamController(SeatMapBroadcaster broadcaster, ShowtimeRepository showtimeRepository)
    {
        this.broadcaster = broadcaster;
        this.showtimeRepository = showtimeRepository;
    }

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Live seat changes of a showtime (server-sent events)", description = """
            Keeps the connection open and sends an event named `seats` whenever seats of this showtime are \
            held, released, sold or come back on sale: `{"showtimeId": 1, "seatIds": [100, 101], "status": "HELD", \
            "holdId": "..."}`. Load the seat map first, then apply these changes to it. A `connected` event is \
            sent when the stream opens; the stream closes after 30 minutes and the browser reconnects by itself.""")
    public SseEmitter stream(@PathVariable Long showtimeId)
    {
        if (!showtimeRepository.existsById(showtimeId)) {
            throw ApiException.notFound(ErrorCode.SHOWTIME_NOT_FOUND, "Showtime %d was not found".formatted(showtimeId));
        }
        return broadcaster.subscribe(showtimeId);
    }
}
