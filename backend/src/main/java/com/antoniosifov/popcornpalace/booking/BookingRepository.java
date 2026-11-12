package com.antoniosifov.popcornpalace.booking;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID>
{
    // A booking is shown as tickets, with its showtime, movie, hall and payment.
    // The entity graphs load those in the same query; the seats of each booking
    // are fetched in batches (see Booking.seats) so that paging stays correct.

    @EntityGraph(attributePaths = {"showtime.movie", "showtime.hall", "payment"})
    Optional<Booking> findDetailedById(UUID id);

    @EntityGraph(attributePaths = {"showtime.movie", "showtime.hall", "payment"})
    Optional<Booking> findDetailedByPaymentId(UUID paymentId);

    @EntityGraph(attributePaths = {"showtime.movie", "showtime.hall", "payment"})
    Page<Booking> findAllByUserId(UUID userId, Pageable pageable);

    /** The bookings of an account that can still be cancelled: confirmed, for a showtime that has not begun. */
    @EntityGraph(attributePaths = {"showtime.movie", "showtime.hall", "payment"})
    @Query("select b from Booking b where b.userId = :userId and b.status = :status and b.showtime.startTime > :now")
    List<Booking> findByUserIdAndStatusStartingAfter(@Param("userId") UUID userId, @Param("status") BookingStatus status,
                                                     @Param("now") Instant now);

    /** Ids of every seat currently sold for a showtime; used to draw the seat map. */
    @Query("select s.seat.id from BookingSeat s where s.showtimeId = :showtimeId and s.active = true")
    Set<Long> findBookedSeatIds(@Param("showtimeId") Long showtimeId);

    /** Of the given seats, the ones currently sold for the showtime. */
    @Query("select s.seat.id from BookingSeat s where s.showtimeId = :showtimeId and s.active = true and s.seat.id in :seatIds")
    Set<Long> findBookedSeatIdsAmong(@Param("showtimeId") Long showtimeId, @Param("seatIds") Collection<Long> seatIds);
}
