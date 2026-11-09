package com.antoniosifov.popcornpalace.booking;

import com.antoniosifov.popcornpalace.booking.SeatMapResponse.Row;
import com.antoniosifov.popcornpalace.booking.SeatMapResponse.SeatStatus;
import com.antoniosifov.popcornpalace.booking.SeatMapResponse.SeatView;
import com.antoniosifov.popcornpalace.common.error.ApiException;
import com.antoniosifov.popcornpalace.common.error.ErrorCode;
import com.antoniosifov.popcornpalace.hall.Hall;
import com.antoniosifov.popcornpalace.hall.Seat;
import com.antoniosifov.popcornpalace.hall.SeatRepository;
import com.antoniosifov.popcornpalace.showtime.Showtime;
import com.antoniosifov.popcornpalace.showtime.ShowtimeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class SeatMapService
{
    private final ShowtimeRepository showtimeRepository;
    private final SeatRepository seatRepository;
    private final BookingRepository bookingRepository;

    public SeatMapService(ShowtimeRepository showtimeRepository, SeatRepository seatRepository,
                          BookingRepository bookingRepository)
    {
        this.showtimeRepository = showtimeRepository;
        this.seatRepository = seatRepository;
        this.bookingRepository = bookingRepository;
    }

    /**
     * Builds the seat map from two flat queries (all seats of the hall, ids of
     * the booked ones) rather than one query per seat or row.
     */
    public SeatMapResponse forShowtime(Long showtimeId)
    {
        Showtime showtime = showtimeRepository.findDetailedById(showtimeId)
                .orElseThrow(() -> ApiException.notFound(ErrorCode.SHOWTIME_NOT_FOUND,
                        "Showtime %d was not found".formatted(showtimeId)));
        Hall hall = showtime.getHall();

        List<Seat> seats = seatRepository.findAllByHallIdOrderByRowNumberAscSeatNumberAsc(hall.getId());
        Set<Long> bookedSeatIds = bookingRepository.findBookedSeatIds(showtimeId);

        // Seats arrive ordered by row, then by seat number: start a new row
        // whenever the row number changes.
        List<Row> rows = new ArrayList<>();
        List<SeatView> currentRow = null;
        int currentRowNumber = -1;
        for (Seat seat : seats) {
            if (seat.getRowNumber() != currentRowNumber) {
                currentRowNumber = seat.getRowNumber();
                currentRow = new ArrayList<>();
                rows.add(new Row(currentRowNumber, currentRow));
            }
            SeatStatus status = bookedSeatIds.contains(seat.getId()) ? SeatStatus.BOOKED : SeatStatus.AVAILABLE;
            currentRow.add(new SeatView(seat.getId(), seat.getSeatNumber(), seat.getCategory(),
                    showtime.priceOf(seat.getCategory()), status));
        }

        return new SeatMapResponse(showtime.getId(), hall.getId(), hall.getName(),
                seats.size(), seats.size() - bookedSeatIds.size(), rows);
    }
}
