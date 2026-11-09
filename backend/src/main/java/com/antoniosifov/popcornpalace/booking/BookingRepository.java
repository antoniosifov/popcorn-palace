package com.antoniosifov.popcornpalace.booking;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID>
{
    // A booking is shown as a ticket, with its seat, showtime, movie and hall.
    // The entity graphs load all of that in one query per call.

    @EntityGraph(attributePaths = {"seat", "showtime.movie", "showtime.hall"})
    Optional<Booking> findDetailedById(UUID id);

    @EntityGraph(attributePaths = {"seat", "showtime.movie", "showtime.hall"})
    Page<Booking> findAllByUserId(UUID userId, Pageable pageable);

    boolean existsByShowtimeIdAndSeatId(Long showtimeId, Long seatId);

    /** Ids of the seats already sold for a showtime; used to draw the seat map. */
    @Query("select b.seat.id from Booking b where b.showtime.id = :showtimeId")
    Set<Long> findBookedSeatIds(@Param("showtimeId") Long showtimeId);
}
