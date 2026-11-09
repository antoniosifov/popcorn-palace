package com.antoniosifov.popcornpalace.seatmap;

import com.antoniosifov.popcornpalace.booking.BookingRepository;
import com.antoniosifov.popcornpalace.hall.SeatRepository;
import com.antoniosifov.popcornpalace.hold.HoldRecord;
import com.antoniosifov.popcornpalace.hold.SeatHoldStore;
import com.antoniosifov.popcornpalace.showtime.ShowtimeRepository;
import com.antoniosifov.popcornpalace.support.ApiIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeatMapApiTest extends ApiIntegrationTest
{
    @Autowired
    private ShowtimeRepository showtimeRepository;
    @Autowired
    private SeatRepository seatRepository;
    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private long movieId;
    private long hallId;
    private long showtimeId;

    @BeforeEach
    void createShowtime() throws Exception
    {
        movieId = createMovie("Interstellar");
        hallId = createHall("Hall 1");
        showtimeId = createShowtime(movieId, hallId, "2030-01-15T18:00:00Z", "2030-01-15T20:49:00Z");
    }

    @DisplayName("The seat map lists every seat row by row with its price and status")
    @Test
    void showsEverySeatWithPriceAndStatus() throws Exception
    {
        long bookedSeatId = seatId(hallId, 1, 2);
        createBooking(showtimeId, bookedSeatId);

        anonymous.perform(get(SHOWTIMES + "/{id}/seats", showtimeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.showtimeId").value(showtimeId))
                .andExpect(jsonPath("$.hallId").value(hallId))
                .andExpect(jsonPath("$.hallName").value("Hall 1"))
                .andExpect(jsonPath("$.totalSeats").value(DEFAULT_SEAT_COUNT))
                .andExpect(jsonPath("$.availableSeats").value(DEFAULT_SEAT_COUNT - 1))
                .andExpect(jsonPath("$.rows.length()").value(3))
                // Row 1: five standard seats, the second one is sold.
                .andExpect(jsonPath("$.rows[0].row").value(1))
                .andExpect(jsonPath("$.rows[0].seats.length()").value(5))
                .andExpect(jsonPath("$.rows[0].seats[0].number").value(1))
                .andExpect(jsonPath("$.rows[0].seats[0].category").value("STANDARD"))
                .andExpect(jsonPath("$.rows[0].seats[0].price").value(14.5))
                .andExpect(jsonPath("$.rows[0].seats[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.rows[0].seats[0].heldByMe").value(false))
                .andExpect(jsonPath("$.rows[0].seats[1].seatId").value(bookedSeatId))
                .andExpect(jsonPath("$.rows[0].seats[1].status").value("BOOKED"))
                // Row 3: four VIP seats at the VIP price.
                .andExpect(jsonPath("$.rows[2].row").value(3))
                .andExpect(jsonPath("$.rows[2].seats.length()").value(4))
                .andExpect(jsonPath("$.rows[2].seats[3].number").value(4))
                .andExpect(jsonPath("$.rows[2].seats[3].category").value("VIP"))
                .andExpect(jsonPath("$.rows[2].seats[3].price").value(22.0));
    }

    @DisplayName("A booking marks the seat only in its own showtime")
    @Test
    void bookingDoesNotAffectAnotherShowtimeInTheSameHall() throws Exception
    {
        long laterShowtimeId = createShowtime(movieId, hallId, "2030-01-15T21:00:00Z", "2030-01-15T23:00:00Z");
        createBooking(showtimeId, seatId(hallId, 1, 2));

        anonymous.perform(get(SHOWTIMES + "/{id}/seats", laterShowtimeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableSeats").value(DEFAULT_SEAT_COUNT))
                .andExpect(jsonPath("$.rows[*].seats[*].status", everyItem(is("AVAILABLE"))));
    }

    @DisplayName("A sold seat shows as BOOKED even if a stale hold key for it is still in Redis")
    @Test
    void databaseWinsOverALeftoverHold() throws Exception
    {
        long seatId = seatId(hallId, 1, 2);
        createBooking(showtimeId, seatId);
        // What would remain if the application died right after saving the booking.
        redis.opsForValue().set("hold:{%d}:seat:%d".formatted(showtimeId, seatId), UUID.randomUUID().toString());

        anonymous.perform(get(SHOWTIMES + "/{id}/seats", showtimeId))
                .andExpect(jsonPath("$.rows[0].seats[1].status").value("BOOKED"))
                .andExpect(jsonPath("$.availableSeats").value(DEFAULT_SEAT_COUNT - 1));
    }

    @DisplayName("The seat map of an unknown showtime returns 404")
    @Test
    void unknownShowtime() throws Exception
    {
        anonymous.perform(get(SHOWTIMES + "/{id}/seats", 999))
                .andExpect(problem(404, "SHOWTIME_NOT_FOUND"));
    }

    @DisplayName("When Redis is down the seat map still works, showing sold seats without holds")
    @Test
    void worksWithoutRedis() throws Exception
    {
        createBooking(showtimeId, seatId(hallId, 1, 2));
        SeatMapService serviceWithBrokenRedis =
                new SeatMapService(showtimeRepository, seatRepository, bookingRepository, new UnreachableHoldStore());

        // Built by hand, so it has no transaction of its own.
        SeatMapResponse seatMap = new TransactionTemplate(transactionManager)
                .execute(tx -> serviceWithBrokenRedis.forShowtime(showtimeId, CUSTOMER_ID));

        assertThat(seatMap.totalSeats()).isEqualTo(DEFAULT_SEAT_COUNT);
        assertThat(seatMap.availableSeats()).isEqualTo(DEFAULT_SEAT_COUNT - 1);
        assertThat(seatMap.rows().get(0).seats().get(1).status()).isEqualTo(SeatMapResponse.SeatStatus.BOOKED);
    }

    /** A hold store that behaves like Redis being unreachable. */
    private static class UnreachableHoldStore implements SeatHoldStore
    {
        private static RedisConnectionFailureException down()
        {
            return new RedisConnectionFailureException("Unable to connect to Redis");
        }

        @Override
        public AcquireResult acquire(HoldRecord hold, Duration ttl)
        {
            throw down();
        }

        @Override
        public Optional<HoldRecord> find(long showtimeId, UUID holdId)
        {
            throw down();
        }

        @Override
        public Optional<HoldRecord> findByUser(long showtimeId, UUID userId)
        {
            throw down();
        }

        @Override
        public boolean keepAlive(HoldRecord hold, Duration atLeast)
        {
            throw down();
        }

        @Override
        public void release(HoldRecord hold)
        {
            throw down();
        }

        @Override
        public Map<Long, UUID> findHolders(long showtimeId, Collection<Long> seatIds)
        {
            throw down();
        }
    }
}
