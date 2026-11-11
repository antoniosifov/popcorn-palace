package com.antoniosifov.popcornpalace.notifications;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The mail settings of a server (MAIL_SECURE=true), read from the same
 * application.yaml the service runs with. Only the mail sender is started.
 */
@SpringBootTest(classes = SecureMailTest.MailOnly.class, properties = {
        "MAIL_HOST=127.0.0.1",
        "MAIL_PORT=3025",
        "MAIL_USERNAME=tickets",
        "MAIL_PASSWORD=not-a-real-password",
        "MAIL_SECURE=true"
})
class SecureMailTest
{
    /** A mail server that offers no encryption, standing in for a connection that cannot be trusted. */
    @RegisterExtension
    static final GreenMailExtension plainServer = new GreenMailExtension(ServerSetupTest.SMTP).withPerMethodLifecycle(false);

    @Autowired
    private JavaMailSender mailSender;

    @DisplayName("With a real mail service configured, the sender logs in with the account it was given")
    @Test
    void usesTheAccount()
    {
        JavaMailSenderImpl sender = (JavaMailSenderImpl) mailSender;

        assertThat(sender.getUsername()).isEqualTo("tickets");
        assertThat(sender.getPassword()).isEqualTo("not-a-real-password");
        assertThat(sender.getJavaMailProperties())
                .containsEntry("mail.smtp.auth", "true")
                .containsEntry("mail.smtp.starttls.required", "true");
    }

    @DisplayName("Neither the password nor an email is sent over a connection that cannot be encrypted")
    @Test
    void refusesAnUnencryptedConnection()
    {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("tickets@example.com");
        message.setTo("customer@example.com");
        message.setSubject("Your tickets");
        message.setText("Row 1, seat 1");

        assertThatThrownBy(() -> mailSender.send(message))
                .isInstanceOf(MailException.class)
                .hasMessageContaining("STARTTLS");
        assertThat(plainServer.getReceivedMessages()).isEmpty();
    }

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration(MailSenderAutoConfiguration.class)
    static class MailOnly
    {
    }
}
