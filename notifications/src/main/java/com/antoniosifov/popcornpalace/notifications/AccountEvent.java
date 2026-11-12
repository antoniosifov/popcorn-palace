package com.antoniosifov.popcornpalace.notifications;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.UUID;

/**
 * An account event as this service reads it: its own class, like
 * {@link BookingEvent}, with only the fields an email needs.
 *
 * <p>{@link PasswordReset#url} is a secret: it lets whoever has it set the
 * account's password. This service puts it into one email and nowhere else,
 * not into a log line and not into the dead-letter topic. The same care goes
 * for {@link #ACCOUNT_DELETED}: its email belongs to somebody who has just
 * asked to be forgotten, so it is used for one message and kept nowhere.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AccountEvent(
        UUID eventId,
        String type,
        Instant occurredAt,
        Account account,
        PasswordReset passwordReset,
        Deletion deletion
)
{
    public static final String PASSWORD_RESET_REQUESTED = "PasswordResetRequested";
    public static final String ACCOUNT_DELETED = "AccountDeleted";

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Account(String email, String displayName)
    {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PasswordReset(String url, Instant expiresAt)
    {
    }

    /** @param cancelledBookings how many upcoming bookings were cancelled and refunded with the account */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Deletion(int cancelledBookings)
    {
    }

    /** How many bookings went with the account; none if the event does not say. */
    public int cancelledBookings()
    {
        return deletion == null ? 0 : Math.max(0, deletion.cancelledBookings());
    }

    /**
     * Checks that the event has what an email needs.
     *
     * @throws InvalidEventException if something required is missing
     */
    public void validate()
    {
        require(eventId != null, "eventId");
        require(type != null && !type.isBlank(), "type");
        require(account != null, "account");
        require(account.email() != null && account.email().contains("@"), "account.email");
        if (PASSWORD_RESET_REQUESTED.equals(type)) {
            require(passwordReset != null, "passwordReset");
            String url = passwordReset.url();
            require(url != null && (url.startsWith("https://") || url.startsWith("http://")), "passwordReset.url");
            require(passwordReset.expiresAt() != null, "passwordReset.expiresAt");
        }
    }

    private static void require(boolean present, String field)
    {
        if (!present) {
            throw new InvalidEventException("Account event is missing " + field);
        }
    }

    /** The link is a secret, and records are printed by whatever logs them. */
    @Override
    public String toString()
    {
        return "AccountEvent[eventId=%s, type=%s]".formatted(eventId, type);
    }
}
