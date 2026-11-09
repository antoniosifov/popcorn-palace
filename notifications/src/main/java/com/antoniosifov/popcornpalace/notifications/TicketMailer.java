package com.antoniosifov.popcornpalace.notifications;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Writes and sends the emails. */
@Component
public class TicketMailer
{
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy, HH:mm", Locale.ENGLISH);

    private final JavaMailSender mailSender;
    private final NotificationProperties properties;

    public TicketMailer(JavaMailSender mailSender, NotificationProperties properties)
    {
        this.mailSender = mailSender;
        this.properties = properties;
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
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.from());
        message.setTo(event.customer().email());
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
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
