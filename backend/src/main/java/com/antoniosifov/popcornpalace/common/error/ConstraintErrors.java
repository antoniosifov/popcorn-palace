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

    private static final Map<String, ConstraintError> BY_CONSTRAINT = Map.of(
            "uq_users_email",
            new ConstraintError(ErrorCode.EMAIL_TAKEN, "An account with this email already exists"),
            "uq_movies_title",
            new ConstraintError(ErrorCode.MOVIE_TITLE_TAKEN, "A movie with this title already exists"),
            "fk_showtimes_movie",
            new ConstraintError(ErrorCode.MOVIE_HAS_SHOWTIMES, "The movie has scheduled showtimes and cannot be deleted"),
            "uq_halls_name",
            new ConstraintError(ErrorCode.HALL_NAME_TAKEN, "A hall with this name already exists"),
            "fk_showtimes_hall",
            new ConstraintError(ErrorCode.HALL_HAS_SHOWTIMES, "The hall has scheduled showtimes and cannot be deleted"),
            "ex_showtimes_hall_time",
            new ConstraintError(ErrorCode.SHOWTIME_OVERLAP, "Another showtime is already scheduled in this hall during this time"),
            "fk_bookings_showtime",
            new ConstraintError(ErrorCode.SHOWTIME_HAS_BOOKINGS, "The showtime has bookings; it cannot be deleted or moved to another hall"),
            "uq_bookings_showtime_seat",
            new ConstraintError(ErrorCode.SEAT_ALREADY_BOOKED, "The seat is already booked for this showtime")
    );

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
