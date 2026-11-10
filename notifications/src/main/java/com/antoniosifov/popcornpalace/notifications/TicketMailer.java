package com.antoniosifov.popcornpalace.notifications;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Writes and sends the emails. */
@Component
public class TicketMailer
{
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy, HH:mm", Locale.ENGLISH);

    /** Names the trace of the booking request that led to the email. */
    static final String TRACE_HEADER = "X-Popcorn-Trace-Id";

    private final JavaMailSender mailSender;
    private final NotificationProperties properties;
    private final Tracer tracer;

    public TicketMailer(JavaMailSender mailSender, NotificationProperties properties, Tracer tracer)
    {
        this.mailSender = mailSender;
        this.properties = properties;
        this.tracer = tracer;
    }

    public void sendTickets(BookingEvent event)
    {
        BookingEvent.Booking booking = event.booking();
        StringBuilder text = new StringBuilder();
        text.append(greeting(event)).append("\n\n");
        text.append("Your booking is confirmed. Here are your tickets.\n\n");
        text.append(showtimeLines(booking)).append('\n');
        text.append("Seats:\n");
        for (BookingEvent.Seat seat : booking.seats()) {
            text.append("  Row %d, seat %d (%s)  %s\n".formatted(
                    seat.row(), seat.seatNumber(), seat.category(), money(seat.price(), booking.currency())));
        }
        text.append("\nTotal paid: ").append(money(booking.totalPrice(), booking.currency())).append('\n');
        text.append("Booking reference: ").append(booking.id()).append("\n\n");
        text.append("Enjoy the film!\nPopcorn Palace\n");

        send(event, "Your tickets for " + booking.movieTitle(), text.toString());
    }

    public void sendCancellation(BookingEvent event)
    {
        BookingEvent.Booking booking = event.booking();
        StringBuilder text = new StringBuilder();
        text.append(greeting(event)).append("\n\n");
        text.append("Your booking has been cancelled and the seats have been released.\n\n");
        text.append(showtimeLines(booking)).append('\n');
        text.append("Refund: ").append(money(booking.totalPrice(), booking.currency()))
                .append(" will be returned to your payment method.\n");
        text.append("Booking reference: ").append(booking.id()).append("\n\n");
        text.append("We hope to see you another time.\nPopcorn Palace\n");

        send(event, "Your booking for " + booking.movieTitle() + " was cancelled", text.toString());
    }

    private void send(BookingEvent event, String subject, String text)
    {
        Span span = tracer.currentSpan();
        mailSender.send(message -> {
            MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.from());
            helper.setTo(event.customer().email());
            helper.setSubject(subject);
            helper.setText(text);
            if (span != null) {
                // The same id the booking request answered with (X-Trace-Id): one
                // trace from the click to the email, findable from either end.
                message.setHeader(TRACE_HEADER, span.context().traceId());
            }
        });
    }

    private static String greeting(BookingEvent event)
    {
        String name = event.customer().displayName();
        return name == null || name.isBlank() ? "Hello," : "Hi " + name + ",";
    }

    private String showtimeLines(BookingEvent.Booking booking)
    {
        String when = WHEN.format(booking.startTime().atZone(properties.timeZone()));
        StringBuilder lines = new StringBuilder();
        lines.append(booking.movieTitle()).append('\n');
        if (booking.hallName() != null) {
            lines.append(booking.hallName()).append('\n');
        }
        lines.append(when).append(" (").append(properties.timeZone().getId()).append(")\n");
        return lines.toString();
    }

    private static String money(BigDecimal amount, String currency)
    {
        if (amount == null) {
            return "-";
        }
        String formatted = amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
        return currency == null ? formatted : formatted + " " + currency;
    }
}
