package com.antoniosifov.popcornpalace.booking;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID>
{
    // A booking is shown as tickets, with its showtime, movie and hall. The
    // entity graphs load those in the same query; the seats of each booking
    // are fetched in batches (see Booking.seats) so that paging stays correct.

    @EntityGraph(attributePaths = {"showtime.movie", "showtime.hall"})
    Optional<Booking> findDetailedById(UUID id);

    @EntityGraph(attributePaths = {"showtime.movie", "showtime.hall"})
    Page<Booking> findAllByUserId(UUID userId, Pageable pageable);

    /** Ids of every seat already sold for a showtime; used to draw the seat map. */
    @Query("select s.seat.id from BookingSeat s where s.showtimeId = :showtimeId")
    Set<Long> findBookedSeatIds(@Param("showtimeId") Long showtimeId);

    /** Of the given seats, the ones already sold for the showtime. */
    @Query("select s.seat.id from BookingSeat s where s.showtimeId = :showtimeId and s.seat.id in :seatIds")
    Set<Long> findBookedSeatIdsAmong(@Param("showtimeId") Long showtimeId, @Param("seatIds") Collection<Long> seatIds);
}
