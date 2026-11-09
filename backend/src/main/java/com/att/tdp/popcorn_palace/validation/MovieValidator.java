package com.att.tdp.popcorn_palace.validation;

import com.att.tdp.popcorn_palace.model.Movie;
import org.springframework.stereotype.Component;

@Component
public class MovieValidator
{
    public void validate (Movie movie)
    {
        if (movie == null) {
            throw new IllegalArgumentException("Movie cannot be null");
        }
        if (movie.getTitle() == null || movie.getTitle().isBlank()) {
            throw new IllegalArgumentException("Title cannot be null");
        }
        if (movie.getTitle().length() > 100) {
            throw new IllegalArgumentException("Movie title is too long (max 100 characters)");
        }
        if (movie.getGenre() == null || movie.getGenre().isBlank()) {
            throw new IllegalArgumentException("Genre cannot be null");
        }
        if (movie.getDuration() <= 0) {
            throw new IllegalArgumentException("Movie duration must be greater than 0 minutes");
        }
        if (movie.getRating() < 0.0 || movie.getRating() > 10.0) {
            throw new IllegalArgumentException("Movie rating must be between 0.0 and 10.0");
        }
        if (movie.getReleaseYear() < 1900 || movie.getReleaseYear() > 2030) {
            throw new IllegalArgumentException("Movie release year must be realistic");
        }
    }

    public String cleanString(String value)
    {
        if (value == null) return null;
        return value.trim().replaceAll("\\s+", " ");
    }
}
