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
}
