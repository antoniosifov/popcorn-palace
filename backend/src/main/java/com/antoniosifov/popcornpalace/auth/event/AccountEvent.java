package com.antoniosifov.popcornpalace.auth.event;

import java.time.Instant;
import java.util.UUID;

/**
 * What is published to Kafka when something happens to an account that
 * another service has to act on. Like a booking event it is a public
 * contract: fields may be added, existing ones are not renamed or removed
 * without a new {@link #version}.
 *
 * <p>Unlike a booking event it can carry a secret: {@link PasswordReset#url}
 * lets whoever holds it set the account's password. It is therefore written to
 * the outbox as confidential, and its topic keeps messages for an hour only.
 *
 * @param eventId identifies this event across redeliveries; consumers use it to ignore duplicates
 * @param type    {@link #PASSWORD_RESET_REQUESTED}
 * @param version schema version of this payload
 */
public record AccountEvent(
        UUID eventId,
        String type,
        int version,
        Instant occurredAt,
        Account account,
        PasswordReset passwordReset
)
{
    public static final String PASSWORD_RESET_REQUESTED = "PasswordResetRequested";
    public static final int CURRENT_VERSION = 1;

    public record Account(UUID id, String email, String displayName)
    {
    }

    /**
     * @param url       the page where the new password is set, with the token in it
     * @param expiresAt the link stops working at this moment
     */
    public record PasswordReset(String url, Instant expiresAt)
    {
    }
}
