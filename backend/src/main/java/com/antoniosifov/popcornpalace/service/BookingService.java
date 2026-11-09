package com.antoniosifov.popcornpalace.service;

import com.antoniosifov.popcornpalace.errors.NotFoundException;
import com.antoniosifov.popcornpalace.model.Booking;
import com.antoniosifov.popcornpalace.model.Showtime;
import com.antoniosifov.popcornpalace.repository.BookingRepository;
import com.antoniosifov.popcornpalace.repository.ShowtimeRepository;
import com.antoniosifov.popcornpalace.validation.BookingValidator;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Transactional
public class BookingService
{
    private final BookingRepository bookingRepository;
    private final ShowtimeRepository showtimeRepository;
    private final BookingValidator bookingValidator;

    public BookingService(BookingRepository bookingRepository, ShowtimeRepository showtimeRepository, BookingValidator bookingValidator)
    {
        this.bookingRepository = bookingRepository;
        this.showtimeRepository = showtimeRepository;
        this.bookingValidator = bookingValidator;
    }

    // Add booking
    public Booking save(Booking booking, Long showtimeId)
    {
        bookingValidator.validateBooking(booking, showtimeId);
        Showtime showtime = showtimeRepository.findById(showtimeId).orElseThrow(() -> new NotFoundException("Showtime not found with ID: " + showtimeId));
        boolean seatTaken = bookingRepository.existsByShowtimeIdAndSeatNumber(showtimeId, booking.getSeatNumber());

        if (seatTaken) {
            throw new IllegalArgumentException("Seat " + booking.getSeatNumber() + " is already booked for this showtime");
        }
        //booking.setId(UUID.randomUUID());
        booking.setShowtime(showtime);
        return bookingRepository.save(booking);
    }
}
