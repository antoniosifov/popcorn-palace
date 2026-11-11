package com.antoniosifov.popcornpalace.notifications;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/** Sends one plain-text email from the cinema. What the email says is up to the caller. */
@Component
class Mail
{
    /** Names the trace of the request that led to the email. */
    static final String TRACE_HEADER = "X-Popcorn-Trace-Id";

    private final JavaMailSender mailSender;
    private final NotificationProperties properties;
    private final Tracer tracer;

    Mail(JavaMailSender mailSender, NotificationProperties properties, Tracer tracer)
    {
        this.mailSender = mailSender;
        this.properties = properties;
        this.tracer = tracer;
    }

    void send(String to, String subject, String text)
    {
        Span span = tracer.currentSpan();
        mailSender.send(message -> {
            MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.from());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text);
            if (span != null) {
                // The same id the request answered with (X-Trace-Id): one trace from
                // the click to the email, findable from either end.
                message.setHeader(TRACE_HEADER, span.context().traceId());
            }
        });
    }

    /** "Hi Anna," for a customer with a name, "Hello," for one without. */
    static String greeting(String displayName)
    {
        return displayName == null || displayName.isBlank() ? "Hello," : "Hi " + displayName + ",";
    }
}
