package com.antoniosifov.popcornpalace.auth;

import com.antoniosifov.popcornpalace.auth.event.AccountEventPublisher;
import com.antoniosifov.popcornpalace.common.error.ApiException;
import com.antoniosifov.popcornpalace.common.error.ErrorCode;
import com.antoniosifov.popcornpalace.common.ratelimit.RateLimitExceededException;
import com.antoniosifov.popcornpalace.common.ratelimit.RateLimiter;
import com.antoniosifov.popcornpalace.security.SessionVersions;
import com.antoniosifov.popcornpalace.user.User;
import com.antoniosifov.popcornpalace.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A forgotten password, replaced through a link sent to the account's email.
 *
 * <p>The link is a bearer secret: whoever has it sets the password. So it is
 * long and random, works once and for a short time, and only its hash is
 * stored. Asking for one never says whether the email has an account.
 */
@Service
public class PasswordResetService
{
    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    /** 256 bits: nothing to guess, which is why a plain SHA-256 of it is enough to store. */
    private static final int TOKEN_BYTES = 32;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountEventPublisher events;
    private final SessionVersions sessionVersions;
    private final RateLimiter rateLimiter;
    private final PasswordResetProperties properties;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final String siteUrl;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                                AccountEventPublisher events, SessionVersions sessionVersions,
                                RateLimiter rateLimiter, PasswordResetProperties properties, JdbcTemplate jdbc,
                                Clock clock, @Value("${app.site-url}") String siteUrl)
    {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
        this.sessionVersions = sessionVersions;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.jdbc = jdbc;
        this.clock = clock;
        this.siteUrl = siteUrl;
    }

    /**
     * Sends a reset link to the email, if it has an account. Says nothing either
     * way: the caller answers the same whether or not anything was sent, so the
     * form cannot be used to find out which emails are registered.
     *
     * @param email already normalized with {@link User#normalizeEmail}
     */
    @Transactional
    public void request(String email)
    {
        Optional<User> found = userRepository.findByEmail(email);
        if (found.isEmpty()) {
            return;
        }
        User user = found.get();
        try {
            // Per account, not per visitor: nobody can make the site write to one mailbox all day.
            rateLimiter.check("password-reset:" + user.getId(), properties.maxEmails(), properties.window());
        }
        catch (RateLimitExceededException exception) {
            log.info("Not sending another password reset email to account {}: {} were sent within {}",
                    user.getId(), properties.maxEmails(), properties.window());
            return;
        }

        String token = newToken();
        Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        Instant expiresAt = now.plus(properties.ttl());
        // The link sent before this one, if any, stops working: only the newest email counts.
        jdbc.update("""
                        INSERT INTO password_reset_tokens (token_hash, user_id, expires_at, created_at)
                        VALUES (?, ?, ?, ?)
                        ON CONFLICT (user_id) DO UPDATE
                            SET token_hash = EXCLUDED.token_hash, expires_at = EXCLUDED.expires_at, created_at = EXCLUDED.created_at""",
                hash(token), user.getId(), Timestamp.from(expiresAt), Timestamp.from(now));

        String url = UriComponentsBuilder.fromUriString(siteUrl)
                .path("/reset-password")
                .queryParam("token", token)
                .build()
                .toUriString();
        events.passwordResetRequested(user, url, expiresAt);
    }

    /**
     * Sets the new password, once, for the account the token was issued to.
     * Every earlier session of that account ends.
     *
     * @throws ApiException 400 RESET_LINK_INVALID for a token that is unknown, expired, replaced by a
     *                      newer one or already used; the answer does not say which
     */
    @Transactional
    public void confirm(String token, String newPassword)
    {
        AuthService.requireUsablePassword(newPassword);

        // Deleting is the check: of two requests with the same token only one finds the row.
        List<UUID> owner = jdbc.queryForList(
                "DELETE FROM password_reset_tokens WHERE token_hash = ? AND expires_at > ? RETURNING user_id",
                UUID.class, hash(token), Timestamp.from(clock.instant()));
        if (owner.isEmpty()) {
            throw ApiException.badRequest(ErrorCode.RESET_LINK_INVALID,
                    "This link has expired or has already been used. Ask for a new one.");
        }
        User user = userRepository.findById(owner.getFirst())
                .orElseThrow(() -> new IllegalStateException("A reset token outlived its account"));

        user.changePassword(passwordEncoder.encode(newPassword));
        // Whoever got here opened a link that was sent to the email: it is theirs.
        user.confirmEmail();
        // Last, and inside the transaction: if the old sessions cannot be ended, the password is not changed.
        sessionVersions.moveTo(user.getId(), user.getSessionVersion());
        log.info("Password of account {} was reset", user.getId());
    }

    /** Links nobody used are of no use to anybody after they expire. */
    @Scheduled(cron = "0 45 * * * *")
    void deleteExpired()
    {
        jdbc.update("DELETE FROM password_reset_tokens WHERE expires_at < ?", Timestamp.from(clock.instant()));
    }

    private String newToken()
    {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token)
    {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Every Java runtime has SHA-256", exception);
        }
    }
}
