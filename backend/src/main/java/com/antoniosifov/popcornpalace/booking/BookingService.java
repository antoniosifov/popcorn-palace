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
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class BookingService
{
    private static final Set<String> SORTABLE_FIELDS = Set.of("createdAt", "price");

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

    @Transactional
    public BookingResponse create(BookingRequest request, CurrentUser user)
    {
        Showtime showtime = showtimeRepository.findDetailedById(request.showtimeId())
                .orElseThrow(() -> ApiException.notFound(ErrorCode.SHOWTIME_NOT_FOUND,
                        "Showtime %d was not found".formatted(request.showtimeId())));
        Seat seat = seatRepository.findById(request.seatId())
                .orElseThrow(() -> ApiException.notFound(ErrorCode.SEAT_NOT_FOUND,
                        "Seat %d was not found".formatted(request.seatId())));

        // Comparing ids only: the seat's hall is not loaded from the database.
        if (!seat.getHall().getId().equals(showtime.getHall().getId())) {
            throw ApiException.badRequest(ErrorCode.SEAT_NOT_IN_HALL,
                    "Seat %d is not in the hall of showtime %d".formatted(seat.getId(), showtime.getId()));
        }

        // Millisecond precision: the value returned now is exactly the value
        // read back later (PostgreSQL does not keep nanoseconds).
        Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        if (!showtime.getStartTime().isAfter(now)) {
            throw ApiException.conflict(ErrorCode.SHOWTIME_ALREADY_STARTED,
                    "Showtime %d has already started and can no longer be booked".formatted(showtime.getId()));
        }

        // Fast path for the common case, with a message that names the seat.
        if (bookingRepository.existsByShowtimeIdAndSeatId(showtime.getId(), seat.getId())) {
            throw ApiException.conflict(ErrorCode.SEAT_ALREADY_BOOKED,
                    "Row %d seat %d is already booked for this showtime".formatted(seat.getRowNumber(), seat.getSeatNumber()));
        }

        BigDecimal price = showtime.priceOf(seat.getCategory());
        if (price == null) {
            // Cannot happen through the API: a showtime is only saved with a price
            // for every category of its hall, and hall layouts never change.
            throw new IllegalStateException("Showtime %d has no price for %s seats".formatted(showtime.getId(), seat.getCategory()));
        }

        // The check above cannot stop two simultaneous requests: both can see the
        // seat as free. The unique constraint uq_bookings_showtime_seat is what
        // guarantees a single winner; the loser's insert fails here and is
        // reported as the same SEAT_ALREADY_BOOKED conflict.
        Booking booking = new Booking(showtime, seat, user.id(), price, now);
        return BookingResponse.from(bookingRepository.saveAndFlush(booking));
    }
}
