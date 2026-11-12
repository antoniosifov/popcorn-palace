package com.antoniosifov.popcornpalace.auth.event;

import com.antoniosifov.popcornpalace.auth.event.AccountEvent.Account;
import com.antoniosifov.popcornpalace.auth.event.AccountEvent.Deletion;
import com.antoniosifov.popcornpalace.auth.event.AccountEvent.PasswordReset;
import com.antoniosifov.popcornpalace.outbox.Outbox;
import com.antoniosifov.popcornpalace.user.User;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Announces what happened to an account, through the outbox, like
 * {@code BookingEventPublisher}: the email is sent if and only if the
 * transaction that asked for it commits.
 */
@Component
public class AccountEventPublisher
{
    private final Outbox outbox;
    private final Clock clock;
    private final String topic;

    public AccountEventPublisher(Outbox outbox, Clock clock, @Value("${app.events.account-topic}") String topic)
    {
        this.outbox = outbox;
        this.clock = clock;
        this.topic = topic;
    }

    /**
     * Call inside the transaction that stores the reset token.
     *
     * @param url the link to send; it is a secret, see {@link AccountEvent}
     */
    public void passwordResetRequested(User user, String url, Instant expiresAt)
    {
        UUID eventId = UUID.randomUUID();
        AccountEvent event = new AccountEvent(
                eventId,
                AccountEvent.PASSWORD_RESET_REQUESTED,
                AccountEvent.CURRENT_VERSION,
                clock.instant().truncatedTo(ChronoUnit.MILLIS),
                new Account(user.getId(), user.getEmail(), user.getDisplayName()),
                new PasswordReset(url, expiresAt),
                null);

        // Keyed by account: if one account asks twice, the emails arrive in the order asked.
        outbox.appendConfidential(topic, user.getId().toString(), event.type(), eventId, event);
    }

    /**
     * Call inside the transaction that deletes the account, before the account
     * is erased: this is the last moment its email is known, and the event takes
     * it along so that one more message can say the account is gone.
     */
    public void accountDeleted(User user, int cancelledBookings)
    {
        UUID eventId = UUID.randomUUID();
        AccountEvent event = new AccountEvent(
                eventId,
                AccountEvent.ACCOUNT_DELETED,
                AccountEvent.CURRENT_VERSION,
                clock.instant().truncatedTo(ChronoUnit.MILLIS),
                new Account(user.getId(), user.getEmail(), user.getDisplayName()),
                null,
                new Deletion(cancelledBookings));

        outbox.appendConfidential(topic, user.getId().toString(), event.type(), eventId, event);
    }

    @Configuration
    static class AccountTopicConfig
    {
        /**
         * Created at startup if it does not exist. Kafka normally keeps messages for a
         * week; these can carry a reset link, or the email of an account that has
         * just been deleted, so they are dropped after about as long as either
         * could matter.
         */
        @Bean
        NewTopic accountEventsTopic(@Value("${app.events.account-topic}") String topic,
                                    @Value("${app.events.account-retention}") Duration retention)
        {
            return TopicBuilder.name(topic)
                    .partitions(1)
                    .replicas(1)
                    .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(retention.toMillis()))
                    .build();
        }
    }
}
