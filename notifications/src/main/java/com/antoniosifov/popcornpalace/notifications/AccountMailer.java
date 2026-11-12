package com.antoniosifov.popcornpalace.notifications;

import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Writes the emails about an account. */
@Component
public class AccountMailer
{
    private static final DateTimeFormatter UNTIL = DateTimeFormatter.ofPattern("HH:mm 'on' d MMMM", Locale.ENGLISH);

    private final Mail mail;
    private final NotificationProperties properties;

    public AccountMailer(Mail mail, NotificationProperties properties)
    {
        this.mail = mail;
        this.properties = properties;
    }

    /**
     * The link to set a new password. The email says what to do if it was not
     * asked for, because the address it goes to is whatever was typed into a
     * public form: its owner may know nothing about it.
     */
    public void sendPasswordReset(AccountEvent event)
    {
        AccountEvent.PasswordReset reset = event.passwordReset();
        String until = UNTIL.format(reset.expiresAt().atZone(properties.timeZone()));

        String text = Mail.greeting(event.account().displayName()) + "\n\n"
                + "Somebody, hopefully you, asked to set a new password for the Popcorn Palace account with this email address.\n\n"
                + "Open this link to choose one:\n"
                + reset.url() + "\n\n"
                + "The link works once, until " + until + " (" + properties.timeZone().getId() + ").\n\n"
                + "If it was not you, there is nothing to do: the password stays as it is, "
                + "and it cannot be changed without this email.\n\n"
                + "Popcorn Palace\n";

        mail.send(event.account().email(), "Set a new password for Popcorn Palace", text);
    }

    /**
     * The last message to an account its owner has deleted: what was done, in
     * case it was done by somebody else at an open browser, and that nothing
     * more will come.
     */
    public void sendFarewell(AccountEvent event)
    {
        int cancelled = event.cancelledBookings();
        StringBuilder text = new StringBuilder();
        text.append(Mail.greeting(event.account().displayName())).append("\n\n");
        text.append("Your Popcorn Palace account has been deleted, as asked from inside the account.\n\n");
        text.append("Your email address, your name and your way of signing in are erased. ");
        if (cancelled == 1) {
            text.append("One booking for a showtime that had not begun was cancelled and refunded; "
                    + "a separate email confirms it. ");
        }
        else if (cancelled > 1) {
            text.append(cancelled).append(" bookings for showtimes that had not begun were cancelled and refunded; "
                    + "a separate email confirms each. ");
        }
        text.append("This is the last email you will get from us.\n\n");
        text.append("If it was not you: a deleted account cannot be brought back, "
                + "but the address is free and you can create a new account with it at any time.\n\n");
        text.append("Popcorn Palace\n");

        mail.send(event.account().email(), "Your Popcorn Palace account has been deleted", text.toString());
    }
}
