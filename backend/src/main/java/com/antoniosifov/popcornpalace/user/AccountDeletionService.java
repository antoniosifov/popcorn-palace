package com.antoniosifov.popcornpalace.user;

import com.antoniosifov.popcornpalace.auth.event.AccountEventPublisher;
import com.antoniosifov.popcornpalace.booking.BookingService;
import com.antoniosifov.popcornpalace.booking.BookingService.CancelledBookings;
import com.antoniosifov.popcornpalace.common.error.ApiException;
import com.antoniosifov.popcornpalace.common.error.ErrorCode;
import com.antoniosifov.popcornpalace.payment.Payment;
import com.antoniosifov.popcornpalace.payment.PaymentService;
import com.antoniosifov.popcornpalace.security.SessionVersions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * An account deleted by its owner.
 *
 * <p>Deleted means that nothing is left that says whose it was. The row in
 * {@code users} stays, with an address that is nobody's, because bookings and
 * payments point at it and a cinema keeps the record of what it sold and
 * refunded. Bookings that could still be used are cancelled and refunded
 * first: a ticket with no owner is a seat nobody can sit in or give back.
 *
 * <p>The email is also in places that are not the {@code users} table: events
 * queued for Kafka carry it, so that an email can be written from them. Those
 * rows are removed here as well, see {@link #forgetQueuedEvents}.
 */
@Service
public class AccountDeletionService
{
    private static final Logger log = LoggerFactory.getLogger(AccountDeletionService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final BookingService bookingService;
    private final PaymentService paymentService;
    private final AccountEventPublisher events;
    private final SessionVersions sessionVersions;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public AccountDeletionService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                                  BookingService bookingService, PaymentService paymentService,
                                  AccountEventPublisher events, SessionVersions sessionVersions, JdbcTemplate jdbc,
                                  PlatformTransactionManager transactionManager, Clock clock)
    {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.bookingService = bookingService;
        this.paymentService = paymentService;
        this.events = events;
        this.sessionVersions = sessionVersions;
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /**
     * Deletes the account, for good. One transaction: the bookings are
     * cancelled, the account erased and its sessions ended, or nothing changed.
     * The refunds follow the commit, as they do for any cancellation; one that
     * fails is retried by the reconciliation job.
     *
     * @param password the account's password, as proof that this is its owner and not
     *                 somebody at a browser left open; ignored for an account that has none
     * @throws ApiException 400 PASSWORD_INCORRECT, 409 ACCOUNT_NOT_DELETABLE for an administrator
     */
    public void delete(UUID userId, String password)
    {
        List<UUID> refundsOwed = transaction.execute(status -> {
            // Held from here to the commit: a booking being paid for right now either
            // finishes first and is cancelled below, or finds the account gone.
            User user = userRepository.findByIdForDeletion(userId)
                    .filter(account -> !account.isDeleted())
                    .orElseThrow(() -> ApiException.unauthorized(ErrorCode.UNAUTHENTICATED, "The account no longer exists"));

            if (user.getRole() == Role.ADMIN) {
                // The cinema would be left with nobody to run it, and the next start
                // would quietly create the administrator again.
                throw ApiException.conflict(ErrorCode.ACCOUNT_NOT_DELETABLE, "An administrator account cannot be deleted");
            }
            if (user.hasPassword() && (password == null || !passwordEncoder.matches(password, user.getPasswordHash()))) {
                throw ApiException.badRequest(ErrorCode.PASSWORD_INCORRECT, "The password is incorrect");
            }

            CancelledBookings cancelled = bookingService.cancelUpcomingOf(userId);
            // While the email is still known: one last message says the account is gone.
            events.accountDeleted(user, cancelled.count());

            jdbc.update("DELETE FROM password_reset_tokens WHERE user_id = ?", userId);
            forgetQueuedEvents(userId);

            Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
            user.erase(now);
            userRepository.flush();
            // Last, and inside the transaction: if the sessions cannot be ended, the account is not deleted.
            sessionVersions.moveTo(userId, user.getSessionVersion());

            log.info("Account {} was deleted by its owner; {} upcoming bookings were cancelled", userId, cancelled.count());
            return cancelled.refundsOwed();
        });

        refundsOwed.forEach(paymentId -> paymentService.refund(paymentId, Payment.BOOKING_CANCELLED));
    }

    /**
     * Events are kept in the outbox for a week after they were sent, for
     * troubleshooting, and each one about this customer carries the email.
     *
     * <p>Two statements, in this order. Events that have not been sent yet,
     * the cancellations and the farewell written a moment ago among them, are
     * marked confidential, which makes the relay delete them as soon as Kafka
     * has them. Then the ones already sent are deleted. The first statement
     * waits for a batch the relay is sending right now, so whatever that batch
     * published is seen, and removed, by the second.
     *
     * <p>What this cannot reach is what has already left: the messages in
     * Kafka, which expire on the topic's schedule, and the emails themselves.
     */
    private void forgetQueuedEvents(UUID userId)
    {
        String id = userId.toString();
        jdbc.update("""
                UPDATE outbox_events SET confidential = TRUE
                WHERE published_at IS NULL
                  AND (payload -> 'customer' ->> 'id' = ? OR payload -> 'account' ->> 'id' = ?)""", id, id);
        jdbc.update("""
                DELETE FROM outbox_events
                WHERE published_at IS NOT NULL
                  AND (payload -> 'customer' ->> 'id' = ? OR payload -> 'account' ->> 'id' = ?)""", id, id);
    }
}
