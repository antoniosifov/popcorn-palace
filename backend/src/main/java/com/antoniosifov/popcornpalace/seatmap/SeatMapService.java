package com.antoniosifov.popcornpalace.seatmap;

import com.antoniosifov.popcornpalace.booking.BookingRepository;
import com.antoniosifov.popcornpalace.common.error.ApiException;
import com.antoniosifov.popcornpalace.common.error.ErrorCode;
import com.antoniosifov.popcornpalace.hall.Hall;
import com.antoniosifov.popcornpalace.hall.Seat;
import com.antoniosifov.popcornpalace.hall.SeatRepository;
import com.antoniosifov.popcornpalace.hold.HoldRecord;
import com.antoniosifov.popcornpalace.hold.SeatHoldStore;
import com.antoniosifov.popcornpalace.seatmap.SeatMapResponse.Row;
import com.antoniosifov.popcornpalace.seatmap.SeatMapResponse.SeatStatus;
import com.antoniosifov.popcornpalace.seatmap.SeatMapResponse.SeatView;
import com.antoniosifov.popcornpalace.showtime.Showtime;
import com.antoniosifov.popcornpalace.showtime.ShowtimeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class SeatMapService
{
    private static final Logger log = LoggerFactory.getLogger(SeatMapService.class);

    private final ShowtimeRepository showtimeRepository;
    private final SeatRepository seatRepository;
    private final BookingRepository bookingRepository;
    private final SeatHoldStore holdStore;

    public SeatMapService(ShowtimeRepository showtimeRepository, SeatRepository seatRepository,
                          BookingRepository bookingRepository, SeatHoldStore holdStore)
    {
        this.showtimeRepository = showtimeRepository;
        this.seatRepository = seatRepository;
        this.bookingRepository = bookingRepository;
        this.holdStore = holdStore;
    }

    /**
     * Builds the seat map from three flat lookups (the hall's seats and the sold
     * seat ids from PostgreSQL, the held seat ids from Redis) rather than one
     * query per seat or row.
     *
     * @param viewerId the logged-in caller, or null for an anonymous visitor
     */
    public SeatMapResponse forShowtime(Long showtimeId, UUID viewerId)
    {
        Showtime showtime = showtimeRepository.findDetailedById(showtimeId)
                .orElseThrow(() -> ApiException.notFound(ErrorCode.SHOWTIME_NOT_FOUND,
                        "Showtime %d was not found".formatted(showtimeId)));
        Hall hall = showtime.getHall();

        List<Seat> seats = seatRepository.findAllByHallIdOrderByRowNumberAscSeatNumberAsc(hall.getId());
        Set<Long> bookedSeatIds = bookingRepository.findBookedSeatIds(showtimeId);
        Holds holds = currentHolds(showtimeId, seats, viewerId);

        // Seats arrive ordered by row, then by seat number: start a new row
        // whenever the row number changes.
        List<Row> rows = new ArrayList<>();
        List<SeatView> currentRow = null;
        int currentRowNumber = -1;
        int available = 0;
        for (Seat seat : seats) {
            if (seat.getRowNumber() != currentRowNumber) {
                currentRowNumber = seat.getRowNumber();
                currentRow = new ArrayList<>();
                rows.add(new Row(currentRowNumber, currentRow));
            }

            UUID holder = holds.bySeat().get(seat.getId());
            SeatStatus status;
            if (bookedSeatIds.contains(seat.getId())) {
                // The database wins: a sold seat is shown as sold even if a
                // leftover hold key for it has not expired yet.
                status = SeatStatus.BOOKED;
            }
            else if (holder != null) {
                status = SeatStatus.HELD;
            }
            else {
                status = SeatStatus.AVAILABLE;
                available++;
            }
            boolean heldByViewer = status == SeatStatus.HELD && holder.equals(holds.viewersHoldId());
            currentRow.add(new SeatView(seat.getId(), seat.getSeatNumber(), seat.getCategory(),
                    showtime.priceOf(seat.getCategory()), status, heldByViewer));
        }

        return new SeatMapResponse(showtime.getId(), hall.getId(), hall.getName(), seats.size(), available, rows);
    }

    private record Holds(Map<Long, UUID> bySeat, UUID viewersHoldId)
    {
    }

    /**
     * Browsing must keep working when Redis is down. Holds cannot be created
     * then either, so showing the map without them loses nothing but freshness.
     */
    private Holds currentHolds(Long showtimeId, List<Seat> seats, UUID viewerId)
    {
        try {
            Map<Long, UUID> bySeat = holdStore.findHolders(showtimeId, seats.stream().map(Seat::getId).toList());
            UUID viewersHoldId = viewerId == null || bySeat.isEmpty()
                    ? null
                    : holdStore.findByUser(showtimeId, viewerId).map(HoldRecord::id).orElse(null);
            return new Holds(bySeat, viewersHoldId);
        }
        catch (DataAccessException exception) {
            log.warn("Seat holds are unavailable; showing the seat map of showtime {} without them", showtimeId, exception);
            return new Holds(Map.of(), null);
        }
    }
}
