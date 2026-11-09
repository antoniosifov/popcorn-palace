package com.antoniosifov.popcornpalace.common.error;

import com.antoniosifov.popcornpalace.booking.Booking;
import com.antoniosifov.popcornpalace.booking.BookingRepository;
import com.antoniosifov.popcornpalace.hall.HallRepository;
import com.antoniosifov.popcornpalace.hall.Seat;
import com.antoniosifov.popcornpalace.hall.SeatCategory;
import com.antoniosifov.popcornpalace.hall.SeatRepository;
import com.antoniosifov.popcornpalace.movie.MovieRepository;
import com.antoniosifov.popcornpalace.showtime.Showtime;
import com.antoniosifov.popcornpalace.showtime.ShowtimeRepository;
import com.antoniosifov.popcornpalace.support.ApiIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * The API tests can only reach the race-protection constraints by actually
 * racing, which does not hit the constraint on every run. These tests write
 * through the repositories directly, skipping the services' checks and the
 * seat holds, so the constraint always fires, and assert that the resulting
 * exception is translated to the right API error.
 */
class ConstraintErrorsIntegrationTest extends ApiIntegrationTest
{
    private static final Map<SeatCategory, BigDecimal> PRICES =
            Map.of(SeatCategory.STANDARD, new BigDecimal("14.50"), SeatCategory.VIP, new BigDecimal("22.00"));

    @Autowired
    private MovieRepository movieRepository;
    @Autowired
    private HallRepository hallRepository;
    @Autowired
    private ShowtimeRepository showtimeRepository;
    @Autowired
    private SeatRepository seatRepository;
    @Autowired
    private BookingRepository bookingRepository;

    @DisplayName("An overlapping showtime written past the service check is reported as SHOWTIME_OVERLAP")
    @Test
    void exclusionConstraintIsTranslated() throws Exception
    {
        long movieId = createMovie("Interstellar");
        long hallId = createHall("Hall 1");
        createShowtime(movieId, hallId, "2030-01-15T18:00:00Z", "2030-01-15T20:49:00Z");

        Showtime overlapping = new Showtime(
                movieRepository.getReferenceById(movieId), hallRepository.getReferenceById(hallId),
                Instant.parse("2030-01-15T19:00:00Z"), Instant.parse("2030-01-15T21:00:00Z"), PRICES);

        Throwable thrown = catchThrowable(() -> showtimeRepository.saveAndFlush(overlapping));

        assertThat(thrown).isInstanceOf(DataAccessException.class);
        assertThat(ConstraintErrors.resolve(thrown))
                .map(ConstraintErrors.ConstraintError::code)
                .contains(ErrorCode.SHOWTIME_OVERLAP);
    }

    @DisplayName("A second sale of a seat, written without any hold, is reported as SEAT_ALREADY_BOOKED")
    @Test
    void uniqueSeatConstraintIsTranslated() throws Exception
    {
        long movieId = createMovie("Interstellar");
        long hallId = createHall("Hall 1");
        long showtimeId = createShowtime(movieId, hallId, "2030-01-15T18:00:00Z", "2030-01-15T20:49:00Z");
        Showtime showtime = showtimeRepository.findDetailedById(showtimeId).orElseThrow();
        Seat seat = seatRepository.findById(seatId(hallId, 1, 1)).orElseThrow();
        Instant now = Instant.parse("2030-01-01T00:00:00Z");

        Booking first = new Booking(showtime, CUSTOMER_ID, now);
        first.addSeat(seat, new BigDecimal("14.50"));
        bookingRepository.saveAndFlush(first);

        Booking second = new Booking(showtime, OTHER_CUSTOMER_ID, now);
        second.addSeat(seat, new BigDecimal("14.50"));
        Throwable thrown = catchThrowable(() -> bookingRepository.saveAndFlush(second));

        assertThat(thrown).isInstanceOf(DataAccessException.class);
        assertThat(ConstraintErrors.resolve(thrown))
                .map(ConstraintErrors.ConstraintError::code)
                .contains(ErrorCode.SEAT_ALREADY_BOOKED);
    }

    @DisplayName("An exception that names no known constraint is not translated")
    @Test
    void unknownFailuresAreNotTranslated()
    {
        assertThat(ConstraintErrors.resolve(new IllegalStateException("violates constraint \"something_else\""))).isEmpty();
        assertThat(ConstraintErrors.resolve(new IllegalStateException())).isEmpty();
    }
}
