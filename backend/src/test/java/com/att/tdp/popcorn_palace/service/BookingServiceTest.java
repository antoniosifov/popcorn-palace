package com.att.tdp.popcorn_palace.service;

import com.att.tdp.popcorn_palace.errors.NotFoundException;
import com.att.tdp.popcorn_palace.model.Booking;
import com.att.tdp.popcorn_palace.model.Showtime;
import com.att.tdp.popcorn_palace.repository.BookingRepository;
import com.att.tdp.popcorn_palace.repository.ShowtimeRepository;
import com.att.tdp.popcorn_palace.validation.BookingValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class BookingServiceTest
{
    private BookingRepository bookingRepository;
    private ShowtimeRepository showtimeRepository;
    private BookingService bookingService;

    @BeforeEach
    void setUp()
    {
        bookingRepository = mock(BookingRepository.class);
        showtimeRepository = mock(ShowtimeRepository.class);
        BookingValidator bookingValidator = new BookingValidator();
        bookingService = new BookingService(bookingRepository, showtimeRepository, bookingValidator);
    }

    @DisplayName("Should save valid booking")
    @Test
    void shouldSaveValidBooking()
    {
        UUID userId = UUID.randomUUID();
        Booking booking = new Booking();
        booking.setUserId(userId);
        booking.setSeatNumber(25);

        Long showtimeId = 100L;
        Showtime showtime = new Showtime();
        showtime.setId(showtimeId);

        when(showtimeRepository.findById(showtimeId)).thenReturn(Optional.of(showtime));
        when(bookingRepository.existsByShowtimeIdAndSeatNumber(showtimeId, 25)).thenReturn(false);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Booking savedBooking = bookingService.save(booking, showtimeId);

        assertNotNull(savedBooking);
        assertEquals(showtime, savedBooking.getShowtime());
        assertEquals(25, savedBooking.getSeatNumber());
        assertEquals(userId, savedBooking.getUserId());
        verify(bookingRepository, times(1)).save(booking);
    }
    @DisplayName("Should throw exception when booking is null")
    @Test
    void shouldThrowExceptionWhenBookingIsNull()
    {
        BookingValidator bookingValidator = new BookingValidator();
        BookingService bookingService = new BookingService(bookingRepository, showtimeRepository, bookingValidator);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            bookingService.save(null, 123L);
        });

        assertEquals("Booking cannot be null", exception.getMessage());
    }

    @DisplayName("Should throw exception when showtimeId is null")
    @Test
    void shouldThrowExceptionWhenShowtimeIdIsNull()
    {
        Booking booking = new Booking();
        booking.setUserId(UUID.randomUUID());
        booking.setSeatNumber(25);

        BookingValidator bookingValidator = new BookingValidator();
        BookingService bookingService = new BookingService(bookingRepository, showtimeRepository, bookingValidator);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            bookingService.save(booking, null);
        });

        assertEquals("Showtime ID is required", exception.getMessage());
    }

    @DisplayName("Should throw exception when userId is null")
    @Test
    void shouldThrowExceptionWhenUserIdIsNull()
    {
        Booking booking = new Booking();
        booking.setSeatNumber(25); // не указываем userId

        Long showtimeId = 1L;

        BookingValidator bookingValidator = new BookingValidator();
        BookingService bookingService = new BookingService(bookingRepository, showtimeRepository, bookingValidator);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            bookingService.save(booking, showtimeId);
        });

        assertEquals("User ID cannot be null", exception.getMessage());
    }

    @DisplayName("Should throw exception when seat number is invalid")
    @Test
    void shouldThrowExceptionWhenSeatNumberInvalid()
    {
        BookingValidator bookingValidator = new BookingValidator();
        BookingRepository bookingRepository = mock(BookingRepository.class);
        ShowtimeRepository showtimeRepository = mock(ShowtimeRepository.class);
        BookingService bookingService = new BookingService(bookingRepository, showtimeRepository, bookingValidator);

        Booking bookingLow = new Booking();
        bookingLow.setUserId(UUID.randomUUID());
        bookingLow.setSeatNumber(0); // invalid

        IllegalArgumentException lowException = assertThrows(IllegalArgumentException.class, () -> {
            bookingService.save(bookingLow, 1L);
        });
        assertEquals("Incorrect seat number", lowException.getMessage());

        Booking bookingHigh = new Booking();
        bookingHigh.setUserId(UUID.randomUUID());
        bookingHigh.setSeatNumber(301); // invalid

        IllegalArgumentException highException = assertThrows(IllegalArgumentException.class, () -> {
            bookingService.save(bookingHigh, 1L);
        });
        assertEquals("Incorrect seat number", highException.getMessage());
    }

    @DisplayName("Should throw exception when seat is already taken")
    @Test
    void shouldThrowExceptionWhenSeatAlreadyTaken()
    {
        Booking booking = new Booking();
        booking.setUserId(UUID.randomUUID());
        booking.setSeatNumber(10);
        Long showtimeId = 1L;

        Showtime showtime = new Showtime();
        showtime.setId(showtimeId);

        BookingRepository bookingRepository = mock(BookingRepository.class);
        ShowtimeRepository showtimeRepository = mock(ShowtimeRepository.class);
        BookingValidator bookingValidator = new BookingValidator();

        when(showtimeRepository.findById(showtimeId)).thenReturn(Optional.of(showtime));
        when(bookingRepository.existsByShowtimeIdAndSeatNumber(showtimeId, 10)).thenReturn(true);

        BookingService bookingService = new BookingService(bookingRepository, showtimeRepository, bookingValidator);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            bookingService.save(booking, showtimeId);
        });

        assertEquals("Seat 10 is already booked for this showtime", exception.getMessage());
    }

    @DisplayName("Should throw exception when showtime is not found")
    @Test
    void shouldThrowExceptionWhenShowtimeNotFound()
    {
        Booking booking = new Booking();
        booking.setUserId(UUID.randomUUID());
        booking.setSeatNumber(10);
        Long showtimeId = 999L;

        BookingRepository bookingRepository = mock(BookingRepository.class);
        ShowtimeRepository showtimeRepository = mock(ShowtimeRepository.class);
        BookingValidator bookingValidator = new BookingValidator();

        when(showtimeRepository.findById(showtimeId)).thenReturn(Optional.empty());

        BookingService bookingService = new BookingService(bookingRepository, showtimeRepository, bookingValidator);

        NotFoundException exception = assertThrows(NotFoundException.class, () -> {
            bookingService.save(booking, showtimeId);
        });

        assertEquals("Showtime not found with ID: 999", exception.getMessage());
    }
}

