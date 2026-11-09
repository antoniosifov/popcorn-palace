package com.antoniosifov.popcornpalace.repository;

import com.antoniosifov.popcornpalace.model.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface BookingRepository extends JpaRepository<Booking,UUID>
{
    boolean existsByShowtimeIdAndSeatNumber(Long showtimeId, int seatNumber);
}

