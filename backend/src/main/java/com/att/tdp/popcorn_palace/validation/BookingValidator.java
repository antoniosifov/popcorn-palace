package com.att.tdp.popcorn_palace.validation;

import com.att.tdp.popcorn_palace.model.Booking;
import org.springframework.stereotype.Component;

@Component
public class BookingValidator
{
    public void validateBooking(Booking booking, Long showtimeId)
    {
        if (booking == null) {
            throw new IllegalArgumentException("Booking cannot be null");
        }
        if (showtimeId == null) {
            throw new IllegalArgumentException("Showtime ID is required");
        }
        if (booking.getUserId() == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (booking.getSeatNumber() < 1 || booking.getSeatNumber() > 300) {
            throw new IllegalArgumentException("Incorrect seat number");
        }
    }
}
