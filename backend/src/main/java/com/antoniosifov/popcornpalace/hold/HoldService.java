package com.antoniosifov.popcornpalace.hold;

import com.antoniosifov.popcornpalace.booking.BookingRepository;
import com.antoniosifov.popcornpalace.booking.BookingResponse;
import com.antoniosifov.popcornpalace.booking.BookingService;
import com.antoniosifov.popcornpalace.common.error.ApiException;
import com.antoniosifov.popcornpalace.common.error.ErrorCode;
import com.antoniosifov.popcornpalace.hall.Seat;
import com.antoniosifov.popcornpalace.hall.SeatRepository;
import com.antoniosifov.popcornpalace.hold.HoldRecord.HeldSeat;
import com.antoniosifov.popcornpalace.hold.SeatHoldStore.AcquireResult;
import com.antoniosifov.popcornpalace.security.CurrentUser;
import com.antoniosifov.popcornpalace.showtime.Showtime;
import com.antoniosifov.popcornpalace.showtime.ShowtimeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The checkout flow: a customer picks seats, they are held for a few minutes,
 * and the hold is then either confirmed into a booking or released (by the
 * customer, or by expiring).
 *
 * <p>Holds exist only in Redis; sold seats exist only in PostgreSQL. Redis
 * keeps other customers off the seats while one of them is checking out.
 * It is not what prevents a double sale: that is the unique constraint on
 * booking_seats, which holds even if Redis loses every key.
 */
@Service
@EnableConfigurationProperties(HoldProperties.class)
public class HoldService
{
    private static final Logger log = LoggerFactory.getLogger(HoldService.class);

    private final SeatHoldStore holdStore;
    private final ShowtimeRepository showtimeRepository;
    private final SeatRepository seatRepository;
    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final HoldProperties properties;
    private final Clock clock;

    public HoldService(SeatHoldStore holdStore, ShowtimeRepository showtimeRepository, SeatRepository seatRepository,
                       BookingRepository bookingRepository, BookingService bookingService,
                       HoldProperties properties, Clock clock)
    {
        this.holdStore = holdStore;
        this.showtimeRepository = showtimeRepository;
        this.seatRepository = seatRepository;
        this.bookingRepository = bookingRepository;
        this.bookingService = bookingService;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public HoldResponse create(Long showtimeId, HoldRequest request, CurrentUser user)
    {
        List<Long> seatIds = request.seatIds();
        if (new HashSet<>(seatIds).size() != seatIds.size()) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "seatIds must not contain the same seat twice");
        }
        if (seatIds.size() > properties.maxSeats()) {
            throw ApiException.badRequest(ErrorCode.TOO_MANY_SEATS,
                    "At most %d seats can be held at once; %d were requested".formatted(properties.maxSeats(), seatIds.size()));
        }

        Showtime showtime = showtimeRepository.findDetailedById(showtimeId)
                .orElseThrow(() -> ApiException.notFound(ErrorCode.SHOWTIME_NOT_FOUND, "Showtime %d was not found".formatted(showtimeId)));
        Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        if (!showtime.getStartTime().isAfter(now)) {
            throw ApiException.conflict(ErrorCode.SHOWTIME_ALREADY_STARTED,
                    "Showtime %d has already started and can no longer be booked".formatted(showtimeId));
        }

        HoldRecord hold = new HoldRecord(UUID.randomUUID(), showtimeId, user.id(),
                seatsOfHall(showtime, seatIds), null, now, now.plus(properties.ttl()));
        hold = withTotal(hold);

        switch (holdStore.acquire(hold, properties.ttl())) {
            case AcquireResult.Acquired acquired -> {
                // taken; continue below
            }
            case AcquireResult.UserAlreadyHasHold existing -> throw ApiException
                    .conflict(ErrorCode.HOLD_ALREADY_EXISTS,
                            "You already hold seats for this showtime; confirm or release that hold first")
                    .with("holdId", existing.existingHoldId());
            case AcquireResult.SeatsTaken taken -> throw ApiException
                    .conflict(ErrorCode.SEAT_ALREADY_HELD, "Some of the seats are being booked by another customer")
                    .with("seatIds", taken.seatIds());
        }

        // Checked only now, with the seats held: a seat can only be sold while
        // its buyer holds it, so from this point on nobody else can sell these
        // seats, and whatever is already sold is visible to this query.
        Set<Long> alreadySold = bookingRepository.findBookedSeatIdsAmong(showtimeId, seatIds);
        if (!alreadySold.isEmpty()) {
            holdStore.release(hold);
            throw ApiException.conflict(ErrorCode.SEAT_ALREADY_BOOKED, "Some of the seats are already booked")
                    .with("seatIds", alreadySold.stream().sorted().toList());
        }
        return HoldResponse.from(hold);
    }

    /** The caller's current hold for the showtime. */
    public HoldResponse getOwn(Long showtimeId, CurrentUser user)
    {
        return holdStore.findByUser(showtimeId, user.id())
                .map(HoldResponse::from)
                .orElseThrow(() -> notFound());
    }

    /**
     * Releasing is idempotent: a hold that has already expired or was already
     * released is simply gone, which is what the caller wanted.
     */
    public void release(Long showtimeId, UUID holdId, CurrentUser user)
    {
        holdStore.find(showtimeId, holdId).ifPresent(hold -> {
            if (!hold.userId().equals(user.id())) {
                // Somebody else's hold: do not reveal that it exists.
                throw notFound();
            }
            holdStore.release(hold);
        });
    }

    /**
     * Turns a hold into a booking. Deliberately not transactional as a whole:
     * the database transaction (inside BookingService) must be committed
     * before the seats are released in Redis. In the opposite order another
     * customer could take a seat between the release and the commit.
     */
    public BookingResponse confirm(Long showtimeId, UUID holdId, CurrentUser user)
    {
        HoldRecord hold = holdStore.find(showtimeId, holdId)
                .filter(found -> found.userId().equals(user.id()))
                .orElseThrow(() -> notFound());

        // Make sure the hold cannot expire while the booking is being written.
        if (!holdStore.keepAlive(hold, properties.confirmGrace())) {
            throw notFound();
        }

        Map<Long, BigDecimal> pricesBySeat = new LinkedHashMap<>();
        hold.seats().forEach(seat -> pricesBySeat.put(seat.seatId(), seat.price()));
        BookingResponse booking = bookingService.create(user.id(), showtimeId, pricesBySeat);

        try {
            holdStore.release(hold);
        }
        catch (DataAccessException exception) {
            // The booking is committed, so this is not an error for the customer.
            // The leftover keys expire on their own, and the seat map reads sold
            // seats from the database, so nothing wrong is ever shown.
            log.warn("Booking {} is saved, but hold {} could not be released; it will expire", booking.id(), hold.id(), exception);
        }
        return booking;
    }

    /** Loads the requested seats and checks that each exists and is in the showtime's hall. */
    private List<HeldSeat> seatsOfHall(Showtime showtime, List<Long> seatIds)
    {
        List<Seat> seats = seatRepository.findAllById(seatIds);
        if (seats.size() != seatIds.size()) {
            Set<Long> missing = new HashSet<>(seatIds);
            seats.forEach(seat -> missing.remove(seat.getId()));
            throw ApiException.notFound(ErrorCode.SEAT_NOT_FOUND, "Some of the seats were not found")
                    .with("seatIds", missing.stream().sorted().toList());
        }
        Long hallId = showtime.getHall().getId();
        List<Long> foreign = seats.stream()
                .filter(seat -> !seat.getHall().getId().equals(hallId))
                .map(Seat::getId)
                .sorted()
                .toList();
        if (!foreign.isEmpty()) {
            throw ApiException.badRequest(ErrorCode.SEAT_NOT_IN_HALL, "Some of the seats are not in the hall of this showtime")
                    .with("seatIds", foreign);
        }
        return seats.stream()
                .sorted(Comparator.comparingInt(Seat::getRowNumber).thenComparingInt(Seat::getSeatNumber))
                .map(seat -> new HeldSeat(seat.getId(), seat.getRowNumber(), seat.getSeatNumber(),
                        seat.getCategory(), showtime.priceOf(seat.getCategory())))
                .toList();
    }

    private static HoldRecord withTotal(HoldRecord hold)
    {
        BigDecimal total = hold.seats().stream().map(HeldSeat::price).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new HoldRecord(hold.id(), hold.showtimeId(), hold.userId(), hold.seats(), total, hold.createdAt(), hold.expiresAt());
    }

    /** One answer for "never existed", "expired" and "belongs to someone else". */
    private static ApiException notFound()
    {
        return ApiException.notFound(ErrorCode.HOLD_NOT_FOUND, "The hold does not exist or has expired");
    }
}
