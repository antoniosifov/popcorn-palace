package com.antoniosifov.popcornpalace.hold;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Where temporary seat holds live. Holds are short-lived and expire on their
 * own, which is exactly what Redis keys with a time to live do; nothing about
 * them is stored in PostgreSQL. See {@link RedisSeatHoldStore}.
 */
public interface SeatHoldStore
{
    /** Outcome of trying to take seats. */
    sealed interface AcquireResult
    {
        /** Every requested seat is now held. */
        record Acquired() implements AcquireResult
        {
        }

        /** Nothing was taken: the user already has a hold for this showtime. */
        record UserAlreadyHasHold(UUID existingHoldId) implements AcquireResult
        {
        }

        /** Nothing was taken: these seats are held by someone else. */
        record SeatsTaken(List<Long> seatIds) implements AcquireResult
        {
        }
    }

    /** Takes all seats of the hold or none of them. */
    AcquireResult acquire(HoldRecord hold, Duration ttl);

    Optional<HoldRecord> find(long showtimeId, UUID holdId);

    /** The hold the user currently has for the showtime, if any. */
    Optional<HoldRecord> findByUser(long showtimeId, UUID userId);

    /**
     * Checks that the hold still owns all of its seats and makes it live at
     * least {@code atLeast} longer.
     *
     * @return false if the hold has expired or lost a seat
     */
    boolean keepAlive(HoldRecord hold, Duration atLeast);

    /** Gives the seats back. Safe to call for a hold that has already expired. */
    void release(HoldRecord hold);

    /** Of the given seats, the ones currently held, mapped to the id of the hold that has them. */
    Map<Long, UUID> findHolders(long showtimeId, Collection<Long> seatIds);
}
