package com.antoniosifov.popcornpalace.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // required by JPA, not for application code
public class User
{
    /** Not a hash of anything: no password matches it. The mark of an account that has none. */
    public static final String NO_PASSWORD = "!";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Always lowercase; see {@link #normalizeEmail}. Nothing changes it except
     * {@link #erase}, which replaces it with an address that is nobody's.
     */
    @Column(nullable = false, length = 254)
    private String email;

    /** A BCrypt hash. The plain password is never stored or logged. */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Raised when the password changes; access tokens issued for an earlier version are refused. */
    @Column(name = "session_version", nullable = false)
    private int sessionVersion;

    /** How Google names the account this one is connected to, if it is. Not the email: that can change there. */
    @Column(name = "google_subject")
    private String googleSubject;

    /** Whether the person behind the account has shown that the email is theirs. */
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    /** When the owner deleted the account; null for an account in use. See {@link #erase}. */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    public User(String email, String passwordHash, String displayName, Role role, Instant createdAt)
    {
        this.email = normalizeEmail(email);
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.role = role;
        this.createdAt = createdAt;
    }

    /**
     * An account opened by signing in with Google: no password of its own, and
     * an email Google has vouched for.
     */
    public static User signedInWithGoogle(String email, String displayName, Role role, Instant createdAt, String googleSubject)
    {
        User user = new User(email, NO_PASSWORD, displayName, role, createdAt);
        user.googleSubject = googleSubject;
        user.emailVerified = true;
        return user;
    }

    /** From now on signing in with this Google account means this account. Google has vouched for the email. */
    public void connectGoogle(String googleSubject)
    {
        this.googleSubject = googleSubject;
        this.emailVerified = true;
    }

    /** The person has opened a link that was sent to the email: it is theirs. */
    public void confirmEmail()
    {
        this.emailVerified = true;
    }

    /**
     * Nobody can log in with a password any more, and nobody who was logged in
     * stays so. A new password can still be set through a reset link.
     */
    public void removePassword()
    {
        changePassword(NO_PASSWORD);
    }

    /**
     * Replaces the password. Whoever was logged in with the old one is not any
     * more: the account moves to its next session version.
     *
     * @param newPasswordHash a BCrypt hash, never the password itself
     */
    public void changePassword(String newPasswordHash)
    {
        this.passwordHash = newPasswordHash;
        this.sessionVersion++;
    }

    /** Whether there is a password to ask for. An account opened with Google has none. */
    public boolean hasPassword()
    {
        return !NO_PASSWORD.equals(passwordHash);
    }

    public boolean isDeleted()
    {
        return deletedAt != null;
    }

    /**
     * Deletes the account at its owner's request. The row stays, because
     * bookings and payments point at it; everything that says whose it was
     * goes. Nobody can log in to it or reach it through Google afterwards, the
     * sessions it had end, and the email is free for a new account.
     */
    public void erase(Instant now)
    {
        this.email = "deleted-" + id + "@deleted.invalid";
        this.displayName = "Deleted customer";
        this.googleSubject = null;
        this.emailVerified = false;
        this.deletedAt = now;
        removePassword();
    }

    /** The single definition of "the same email": trimmed and lowercased. */
    public static String normalizeEmail(String email)
    {
        return email == null ? null : email.strip().toLowerCase();
    }
}
