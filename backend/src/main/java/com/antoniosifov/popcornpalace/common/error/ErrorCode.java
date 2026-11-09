package com.antoniosifov.popcornpalace.common.error;

/**
 * Stable, machine-readable identifiers returned in the {@code code} field of
 * every error response. Clients branch on these instead of parsing messages.
 */
public enum ErrorCode
{
    VALIDATION_FAILED,
    MALFORMED_REQUEST,
    INVALID_SORT,

    MOVIE_NOT_FOUND,
    MOVIE_TITLE_TAKEN,
    MOVIE_HAS_SHOWTIMES,

    SHOWTIME_NOT_FOUND,
    SHOWTIME_OVERLAP,
    SHOWTIME_HAS_BOOKINGS,
    SHOWTIME_ALREADY_STARTED,

    BOOKING_NOT_FOUND,
    SEAT_ALREADY_BOOKED,

    DATA_CONFLICT,
    INTERNAL_ERROR
}
