package com.antoniosifov.popcornpalace.booking;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID>
{
    @EntityGraph(attributePaths = "seat")
    Optional<Booking> findWithSeatById(UUID id);

    boolean existsByShowtimeIdAndSeatId(Long showtimeId, Long seatId);

    /** Ids of the seats already sold for a showtime; used to draw the seat map. */
    @Query("select b.seat.id from Booking b where b.showtime.id = :showtimeId")
    Set<Long> findBookedSeatIds(@Param("showtimeId") Long showtimeId);
}
