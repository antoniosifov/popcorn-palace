package com.antoniosifov.popcornpalace.booking;

import com.antoniosifov.popcornpalace.common.error.ApiException;
import com.antoniosifov.popcornpalace.common.error.ErrorCode;
import com.antoniosifov.popcornpalace.common.web.PageResponse;
import com.antoniosifov.popcornpalace.common.web.SortFields;
import com.antoniosifov.popcornpalace.hall.Seat;
import com.antoniosifov.popcornpalace.hall.SeatRepository;
import com.antoniosifov.popcornpalace.security.CurrentUser;
import com.antoniosifov.popcornpalace.showtime.Showtime;
import com.antoniosifov.popcornpalace.showtime.ShowtimeRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class BookingService
{
    private static final Set<String> SORTABLE_FIELDS = Set.of("createdAt", "totalPrice");

    private final BookingRepository bookingRepository;
    private final ShowtimeRepository showtimeRepository;
    private final SeatRepository seatRepository;
    private final Clock clock;

    public BookingService(BookingRepository bookingRepository, ShowtimeRepository showtimeRepository,
                          SeatRepository seatRepository, Clock clock)
    {
        this.bookingRepository = bookingRepository;
        this.showtimeRepository = showtimeRepository;
        this.seatRepository = seatRepository;
        this.clock = clock;
    }

    /** The caller's own bookings. */
    public PageResponse<BookingResponse> listOwn(CurrentUser user, Pageable pageable)
    {
        SortFields.requireAllowed(pageable, SORTABLE_FIELDS);
        return PageResponse.from(bookingRepository.findAllByUserId(user.id(), pageable).map(BookingResponse::from));
    }

    /**
     * A booking is visible to its owner and to administrators. For anyone else
     * it "does not exist": answering 403 instead would confirm that the id is real.
     */
    public BookingResponse get(UUID id, CurrentUser user)
    {
        return bookingRepository.findDetailedById(id)
                .filter(booking -> user.isAdmin() || booking.getUserId().equals(user.id()))
                .map(BookingResponse::from)
                .orElseThrow(() -> ApiException.notFound(ErrorCode.BOOKING_NOT_FOUND, "Booking %s was not found".formatted(id)));
    }

    /**
     * Records the sale of the given seats in one transaction: either every
     * seat is booked or none is. Called when a hold is confirmed.
     *
     * <p>This method does not check that the seats are free. It does not need
     * to: the unique constraint uq_booking_seats_showtime_seat rejects a seat
     * that is already sold, the whole transaction rolls back, and the caller
     * gets a SEAT_ALREADY_BOOKED conflict. No check made here could be as
     * reliable, because another transaction can always commit right after it.
     *
     * @param pricesBySeat the price to charge for each seat id
     */
    @Transactional
    public BookingResponse create(UUID userId, Long showtimeId, Map<Long, BigDecimal> pricesBySeat)
    {
        Showtime showtime = showtimeRepository.findDetailedById(showtimeId)
                .orElseThrow(() -> ApiException.notFound(ErrorCode.SHOWTIME_NOT_FOUND, "Showtime %d was not found".formatted(showtimeId)));

        // Millisecond precision: the value returned now is exactly the value
        // read back later (PostgreSQL does not keep nanoseconds).
        Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        if (!showtime.getStartTime().isAfter(now)) {
            throw ApiException.conflict(ErrorCode.SHOWTIME_ALREADY_STARTED,
                    "Showtime %d has already started and can no longer be booked".formatted(showtimeId));
        }

        List<Seat> seats = seatRepository.findAllById(pricesBySeat.keySet());
        if (seats.size() != pricesBySeat.size()) {
            throw ApiException.notFound(ErrorCode.SEAT_NOT_FOUND, "Some of the seats were not found");
        }

        Booking booking = new Booking(showtime, userId, now);
        seats.stream()
                .sorted(Comparator.comparingInt(Seat::getRowNumber).thenComparingInt(Seat::getSeatNumber))
                .forEach(seat -> booking.addSeat(seat, pricesBySeat.get(seat.getId())));
        return BookingResponse.from(bookingRepository.saveAndFlush(booking));
    }
}
