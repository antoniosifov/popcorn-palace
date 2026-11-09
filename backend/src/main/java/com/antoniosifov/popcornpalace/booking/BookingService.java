package com.antoniosifov.popcornpalace.booking;

import com.antoniosifov.popcornpalace.booking.event.BookingEventPublisher;
import com.antoniosifov.popcornpalace.common.error.ApiException;
import com.antoniosifov.popcornpalace.common.error.ErrorCode;
import com.antoniosifov.popcornpalace.common.web.PageResponse;
import com.antoniosifov.popcornpalace.common.web.SortFields;
import com.antoniosifov.popcornpalace.hall.Seat;
import com.antoniosifov.popcornpalace.hall.SeatRepository;
import com.antoniosifov.popcornpalace.hall.SeatStatusChanged;
import com.antoniosifov.popcornpalace.payment.Payment;
import com.antoniosifov.popcornpalace.payment.PaymentRepository;
import com.antoniosifov.popcornpalace.payment.PaymentService;
import com.antoniosifov.popcornpalace.payment.PaymentStatus;
import com.antoniosifov.popcornpalace.security.CurrentUser;
import com.antoniosifov.popcornpalace.showtime.Showtime;
import com.antoniosifov.popcornpalace.showtime.ShowtimeRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final BookingEventPublisher events;
    private final ApplicationEventPublisher applicationEvents;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public BookingService(BookingRepository bookingRepository, ShowtimeRepository showtimeRepository,
                          SeatRepository seatRepository, PaymentRepository paymentRepository,
                          PaymentService paymentService, BookingEventPublisher events,
                          ApplicationEventPublisher applicationEvents,
                          PlatformTransactionManager transactionManager, Clock clock)
    {
        this.bookingRepository = bookingRepository;
        this.showtimeRepository = showtimeRepository;
        this.seatRepository = seatRepository;
        this.paymentRepository = paymentRepository;
        this.paymentService = paymentService;
        this.events = events;
        this.applicationEvents = applicationEvents;
        this.transaction = new TransactionTemplate(transactionManager);
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
        return BookingResponse.from(requireVisible(id, user));
    }

    /** The booking a payment paid for, if the booking was saved. */
    public Optional<BookingResponse> findByPayment(UUID paymentId)
    {
        return bookingRepository.findDetailedByPaymentId(paymentId).map(BookingResponse::from);
    }

    public BookingResponse getByPayment(UUID paymentId)
    {
        return findByPayment(paymentId)
                .orElseThrow(() -> new IllegalStateException("Payment %s succeeded but has no booking".formatted(paymentId)));
    }

    /**
     * Records the sale of the given seats, marks the payment as succeeded and
     * queues the "booking confirmed" event, in one transaction: either all
     * three happened or none did. Called after the customer has been charged.
     *
     * <p>This method does not check that the seats are free. It does not need
     * to: the unique index uq_booking_seats_showtime_seat rejects a seat that
     * is already sold, the whole transaction rolls back, and the caller gets a
     * SEAT_ALREADY_BOOKED conflict (and refunds the charge). No check made here
     * could be as reliable, because another transaction can always commit right
     * after it.
     *
     * @param pricesBySeat the price to charge for each seat id
     */
    @Transactional
    public BookingResponse create(UUID userId, Long showtimeId, Map<Long, BigDecimal> pricesBySeat, UUID paymentId)
    {
        Showtime showtime = showtimeRepository.findDetailedById(showtimeId)
                .orElseThrow(() -> ApiException.notFound(ErrorCode.SHOWTIME_NOT_FOUND, "Showtime %d was not found".formatted(showtimeId)));

        Instant now = now();
        if (!showtime.getStartTime().isAfter(now)) {
            throw ApiException.conflict(ErrorCode.SHOWTIME_ALREADY_STARTED,
                    "Showtime %d has already started and can no longer be booked".formatted(showtimeId));
        }

        List<Seat> seats = seatRepository.findAllById(pricesBySeat.keySet());
        if (seats.size() != pricesBySeat.size()) {
            throw ApiException.notFound(ErrorCode.SEAT_NOT_FOUND, "Some of the seats were not found");
        }

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalStateException("Payment %s does not exist".formatted(paymentId)));
        payment.markSucceeded(now);

        Booking booking = new Booking(showtime, userId, payment, now);
        seats.stream()
                .sorted(Comparator.comparingInt(Seat::getRowNumber).thenComparingInt(Seat::getSeatNumber))
                .forEach(seat -> booking.addSeat(seat, pricesBySeat.get(seat.getId())));
        Booking saved = bookingRepository.saveAndFlush(booking);

        // Written to the outbox in this same transaction: the event exists if
        // and only if the booking does.
        events.confirmed(saved);
        // Browsers hear about it once this transaction commits.
        applicationEvents.publishEvent(SeatStatusChanged.booked(showtimeId, List.copyOf(pricesBySeat.keySet())));
        return BookingResponse.from(saved);
    }

    /**
     * Cancels a booking: its seats go back on sale at once and the payment is
     * refunded. Cancelling an already cancelled booking changes nothing and
     * returns it as it is, so a repeated request is harmless.
     *
     * <p>Not one transaction on purpose. The cancellation is committed first,
     * together with the fact that a refund is owed (REFUND_PENDING). Only then
     * is the payment provider called. If that call fails, the customer still
     * sees a cancelled booking and the reconciliation job retries the refund;
     * a provider outage never blocks a cancellation or leaves it half-done.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public BookingResponse cancel(UUID id, CurrentUser user)
    {
        Optional<UUID> refundOwed = transaction.execute(status -> {
            Booking booking = requireVisible(id, user);
            if (booking.isCancelled()) {
                return Optional.<UUID>empty();
            }
            Instant now = now();
            if (!booking.getShowtime().getStartTime().isAfter(now)) {
                throw ApiException.conflict(ErrorCode.SHOWTIME_ALREADY_STARTED,
                        "The showtime has already started; the booking can no longer be cancelled");
            }
            booking.cancel(now);
            events.cancelled(booking);
            applicationEvents.publishEvent(SeatStatusChanged.available(booking.getShowtime().getId(),
                    booking.getSeats().stream().map(seat -> seat.getSeat().getId()).toList()));

            Payment payment = booking.getPayment();
            if (payment != null && payment.getStatus() == PaymentStatus.SUCCEEDED) {
                payment.markRefundPending(now);
                return Optional.of(payment.getId());
            }
            return Optional.<UUID>empty();
        });

        refundOwed.ifPresent(paymentId -> paymentService.refund(paymentId, Payment.BOOKING_CANCELLED));

        // Read again, so the response shows what the refund attempt left behind.
        return transaction.execute(status -> BookingResponse.from(requireVisible(id, user)));
    }

    private Booking requireVisible(UUID id, CurrentUser user)
    {
        return bookingRepository.findDetailedById(id)
                .filter(booking -> user.isAdmin() || booking.getUserId().equals(user.id()))
                .orElseThrow(() -> ApiException.notFound(ErrorCode.BOOKING_NOT_FOUND, "Booking %s was not found".formatted(id)));
    }

    /**
     * Millisecond precision: the value returned now is exactly the value read
     * back later (PostgreSQL does not keep nanoseconds).
     */
    private Instant now()
    {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }
}
