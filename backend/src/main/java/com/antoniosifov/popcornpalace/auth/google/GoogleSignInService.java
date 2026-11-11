package com.antoniosifov.popcornpalace.auth.google;

import com.antoniosifov.popcornpalace.auth.AuthResponse;
import com.antoniosifov.popcornpalace.auth.TokenService;
import com.antoniosifov.popcornpalace.auth.google.GoogleIdTokenVerifier.GoogleAccount;
import com.antoniosifov.popcornpalace.common.error.ApiException;
import com.antoniosifov.popcornpalace.common.error.ErrorCode;
import com.antoniosifov.popcornpalace.common.text.Text;
import com.antoniosifov.popcornpalace.security.SessionVersions;
import com.antoniosifov.popcornpalace.user.Role;
import com.antoniosifov.popcornpalace.user.User;
import com.antoniosifov.popcornpalace.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Signing in with Google: finds the account, connects one that already uses
 * the email, or opens a new one.
 *
 * <p>The delicate case is the middle one. Registration with a password asks
 * for no proof that the email is the registrant's, so an account with
 * somebody's email may have been opened by somebody else, on purpose, ahead of
 * them. If the rightful owner then signs in with Google and the two are simply
 * joined, the stranger keeps a working password to the owner's account. So an
 * account whose email was never proven loses its password and its sessions
 * the moment Google vouches for the owner; an account that did prove its
 * email (through a reset link, or an earlier Google sign-in) is the owner's
 * own, and keeps both.
 */
@Service
public class GoogleSignInService
{
    private static final Logger log = LoggerFactory.getLogger(GoogleSignInService.class);
    private static final int MAX_NAME_LENGTH = 100;

    private final GoogleIdTokenVerifier verifier;
    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final SessionVersions sessionVersions;
    private final Clock clock;

    public GoogleSignInService(GoogleIdTokenVerifier verifier, UserRepository userRepository,
                               TokenService tokenService, SessionVersions sessionVersions, Clock clock)
    {
        this.verifier = verifier;
        this.userRepository = userRepository;
        this.tokenService = tokenService;
        this.sessionVersions = sessionVersions;
        this.clock = clock;
    }

    @Transactional
    public AuthResponse signIn(String idToken, String nonce)
    {
        GoogleAccount google = verifier.verify(idToken, nonce);
        if (!google.emailVerified()) {
            // Google lets an address be used before its owner has confirmed it. Such an
            // address proves nothing about who is signing in.
            throw ApiException.unauthorized(ErrorCode.GOOGLE_EMAIL_UNVERIFIED,
                    "Google has not confirmed this email address yet. Confirm it with Google and try again.");
        }

        User user = userRepository.findByGoogleSubject(google.subject())
                .orElseGet(() -> connectOrCreate(google));
        return AuthResponse.of(tokenService.issue(user.getId(), user.getRole(), user.getSessionVersion()), user);
    }

    private User connectOrCreate(GoogleAccount google)
    {
        String email = User.normalizeEmail(google.email());
        Optional<User> existing = userRepository.findByEmail(email);
        if (existing.isEmpty()) {
            User created = User.signedInWithGoogle(email, displayName(google, email), Role.CUSTOMER,
                    clock.instant().truncatedTo(ChronoUnit.MILLIS), google.subject());
            log.info("Opened an account for a Google sign-in");
            return userRepository.saveAndFlush(created);
        }

        User account = existing.get();
        if (account.getGoogleSubject() != null) {
            // The email now belongs to another Google account than the one connected here.
            throw ApiException.conflict(ErrorCode.EMAIL_TAKEN,
                    "An account with this email is connected to another Google account");
        }
        boolean proven = account.isEmailVerified();
        account.connectGoogle(google.subject());
        if (!proven) {
            account.removePassword();
            sessionVersions.moveTo(account.getId(), account.getSessionVersion());
            log.info("Connected account {} to Google; its email had never been proven, so its password and sessions are gone",
                    account.getId());
        }
        else {
            log.info("Connected account {} to Google", account.getId());
        }
        return account;
    }

    /** The name Google gives, or the part of the email before the "@" when it gives none. */
    private static String displayName(GoogleAccount google, String email)
    {
        String name = Text.normalize(google.name());
        if (name == null || name.isBlank()) {
            name = email.substring(0, email.indexOf('@'));
        }
        return name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH).strip() : name;
    }
}
