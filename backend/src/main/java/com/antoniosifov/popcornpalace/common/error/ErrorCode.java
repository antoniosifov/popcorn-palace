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

    UNAUTHENTICATED,
    FORBIDDEN,
    INVALID_CREDENTIALS,
    EMAIL_TAKEN,

    MOVIE_NOT_FOUND,
    MOVIE_TITLE_TAKEN,
    MOVIE_HAS_SHOWTIMES,

    HALL_NOT_FOUND,
    HALL_NAME_TAKEN,
    HALL_TOO_LARGE,
    HALL_HAS_SHOWTIMES,

    SHOWTIME_NOT_FOUND,
    SHOWTIME_OVERLAP,
    SHOWTIME_PRICE_MISSING,
    SHOWTIME_HAS_BOOKINGS,
    SHOWTIME_ALREADY_STARTED,

    BOOKING_NOT_FOUND,
    SEAT_NOT_FOUND,
    SEAT_NOT_IN_HALL,
    SEAT_ALREADY_BOOKED,

    DATA_CONFLICT,
    INTERNAL_ERROR
}
