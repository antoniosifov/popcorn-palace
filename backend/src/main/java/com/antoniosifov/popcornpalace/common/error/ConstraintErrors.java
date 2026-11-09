package com.antoniosifov.popcornpalace.common.error;

import org.hibernate.exception.ConstraintViolationException;

import java.util.Map;
import java.util.Optional;

/**
 * Maps database constraint names (defined in the Flyway migrations) to API errors.
 *
 * <p>The database is the only place that can enforce these rules when requests
 * race each other, so the application lets the constraint fire and translates
 * the violation instead of trying to prevent it with a check-then-write.
 */
final class ConstraintErrors
{
    record ConstraintError(ErrorCode code, String message)
    {
    }

    private static final Map<String, ConstraintError> BY_CONSTRAINT = Map.ofEntries(
            entry("uq_users_email", ErrorCode.EMAIL_TAKEN,
                    "An account with this email already exists"),
            entry("uq_movies_title", ErrorCode.MOVIE_TITLE_TAKEN,
                    "A movie with this title already exists"),
            entry("fk_showtimes_movie", ErrorCode.MOVIE_HAS_SHOWTIMES,
                    "The movie has scheduled showtimes and cannot be deleted"),
            entry("uq_halls_name", ErrorCode.HALL_NAME_TAKEN,
                    "A hall with this name already exists"),
            entry("fk_showtimes_hall", ErrorCode.HALL_HAS_SHOWTIMES,
                    "The hall has scheduled showtimes and cannot be deleted"),
            entry("ex_showtimes_hall_time", ErrorCode.SHOWTIME_OVERLAP,
                    "Another showtime is already scheduled in this hall during this time"),
            entry("fk_bookings_showtime", ErrorCode.SHOWTIME_HAS_BOOKINGS,
                    "The showtime has bookings; it cannot be deleted or moved to another hall"),
            entry("fk_booking_seats_showtime", ErrorCode.SHOWTIME_HAS_BOOKINGS,
                    "The showtime has bookings; it cannot be deleted or moved to another hall"),
            entry("uq_booking_seats_showtime_seat", ErrorCode.SEAT_ALREADY_BOOKED,
                    "One of the seats is already booked for this showtime"),
            entry("uq_payments_user_idempotency_key", ErrorCode.PAYMENT_IN_PROGRESS,
                    "A payment with this Idempotency-Key is already being processed"),
            entry("uq_bookings_payment", ErrorCode.PAYMENT_IN_PROGRESS,
                    "A booking for this payment is already being created")
    );

    private static Map.Entry<String, ConstraintError> entry(String constraint, ErrorCode code, String message)
    {
        return Map.entry(constraint, new ConstraintError(code, message));
    }

    private ConstraintErrors()
    {
    }

    /** Finds the API error for the constraint that caused the given exception, if it is a known one. */
    static Optional<ConstraintError> resolve(Throwable exception)
    {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            // Preferred: Hibernate already extracted the constraint name.
            if (cause instanceof ConstraintViolationException violation && violation.getConstraintName() != null) {
                ConstraintError error = BY_CONSTRAINT.get(violation.getConstraintName().toLowerCase());
                if (error != null) {
                    return Optional.of(error);
                }
            }
            // Fallback: Hibernate does not extract the name for every kind of
            // violation (exclusion constraints, for one), but PostgreSQL always
            // quotes it in the error message.
            String message = cause.getMessage();
            if (message != null) {
                for (Map.Entry<String, ConstraintError> entry : BY_CONSTRAINT.entrySet()) {
                    if (message.contains('"' + entry.getKey() + '"')) {
                        return Optional.of(entry.getValue());
                    }
                }
            }
        }
        return Optional.empty();
    }
}
