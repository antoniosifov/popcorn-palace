package com.antoniosifov.popcornpalace.booking;

import com.antoniosifov.popcornpalace.common.error.ApiException;
import com.antoniosifov.popcornpalace.common.error.ErrorCode;
import com.antoniosifov.popcornpalace.showtime.Showtime;
import com.antoniosifov.popcornpalace.showtime.ShowtimeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class BookingService
{
    private final BookingRepository bookingRepository;
    private final ShowtimeRepository showtimeRepository;
    private final Clock clock;

    public BookingService(BookingRepository bookingRepository, ShowtimeRepository showtimeRepository, Clock clock)
    {
        this.bookingRepository = bookingRepository;
        this.showtimeRepository = showtimeRepository;
        this.clock = clock;
    }

    public BookingResponse get(UUID id)
    {
        return bookingRepository.findById(id)
                .map(BookingResponse::from)
                .orElseThrow(() -> ApiException.notFound(ErrorCode.BOOKING_NOT_FOUND, "Booking %s was not found".formatted(id)));
    }

    @Transactional
    public BookingResponse create(BookingRequest request)
    {
        Showtime showtime = showtimeRepository.findById(request.showtimeId())
                .orElseThrow(() -> ApiException.notFound(ErrorCode.SHOWTIME_NOT_FOUND,
                        "Showtime %d was not found".formatted(request.showtimeId())));

        // Millisecond precision: the value returned now is exactly the value
        // read back later (PostgreSQL does not keep nanoseconds).
        Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        if (!showtime.getStartTime().isAfter(now)) {
            throw ApiException.conflict(ErrorCode.SHOWTIME_ALREADY_STARTED,
                    "Showtime %d has already started and can no longer be booked".formatted(showtime.getId()));
        }

        // Fast path for the common case, with a message that names the seat.
        if (bookingRepository.existsByShowtimeIdAndSeatNumber(showtime.getId(), request.seatNumber())) {
            throw ApiException.conflict(ErrorCode.SEAT_ALREADY_BOOKED,
                    "Seat %d is already booked for this showtime".formatted(request.seatNumber()));
        }

        // The check above cannot stop two simultaneous requests: both can see the
        // seat as free. The unique constraint uq_bookings_showtime_seat is what
        // guarantees a single winner; the loser's insert fails here and is
        // reported as the same SEAT_ALREADY_BOOKED conflict.
        Booking booking = new Booking(showtime, request.seatNumber(), request.userId(), now);
        return BookingResponse.from(bookingRepository.saveAndFlush(booking));
    }
}
