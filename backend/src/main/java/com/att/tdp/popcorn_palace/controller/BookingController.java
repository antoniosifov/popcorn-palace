package com.att.tdp.popcorn_palace.controller;

import com.att.tdp.popcorn_palace.model.Booking;
import com.att.tdp.popcorn_palace.service.BookingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/bookings")
public class BookingController
{
    private final BookingService bookingService;

    public BookingController (BookingService bookingService)
    {
        this.bookingService = bookingService;
    }

    @PostMapping
    public ResponseEntity<Map<String, UUID>> addBooking (@RequestBody Booking booking)
    {
        Booking savedBooking = bookingService.save(booking, booking.getShowtime().getId());
        return ResponseEntity.ok(Map.of("bookingId", savedBooking.getId()));
    }
}
