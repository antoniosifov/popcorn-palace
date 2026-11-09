package com.att.tdp.popcorn_palace.validation;

import com.att.tdp.popcorn_palace.model.Showtime;
import org.springframework.stereotype.Component;

@Component
public class ShowtimeValidator
{
    public void validate(Showtime showtime)
    {
        if (showtime == null) {
            throw new IllegalArgumentException("Showtime cannot be null");
        }
        if (showtime.getMovie() == null) {
            throw new IllegalArgumentException("Movie cannot be null");
        }
        if (showtime.getMovie().getId() == null) {
            throw new IllegalArgumentException("Movie ID is required");
        }
        if (showtime.getTheater() == null || showtime.getTheater().isBlank()) {
            throw new IllegalArgumentException("Theater cannot be null");
        }
        if (showtime.getStart_time() == null || showtime.getEnd_time() == null) {
            throw new IllegalArgumentException("Start time and End time cannot be null");
        }
        if (showtime.getStart_time().isAfter(showtime.getEnd_time())) {
            throw new IllegalArgumentException("Start time cannot be after end time");
        }
        if (showtime.getPrice() < 0.0) {
            throw new IllegalArgumentException("Price cannot be negative");
        }
    }

    public String cleanString(String value)
    {
        if (value == null) return null;
        return value.trim().replaceAll("\\s+", " ");
    }
}
