package com.att.tdp.popcorn_palace.service;

import com.att.tdp.popcorn_palace.errors.NotFoundException;
import com.att.tdp.popcorn_palace.model.Movie;
import com.att.tdp.popcorn_palace.repository.MovieRepository;
import com.att.tdp.popcorn_palace.validation.MovieValidator;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class MovieService
{
    private final MovieRepository movieRepository;
    private final MovieValidator movieValidator;

    public MovieService(MovieRepository movieRepository, MovieValidator movieValidator)
    {
        this.movieRepository = movieRepository;
        this.movieValidator = movieValidator;
    }

    // Get all movies
    public List<Movie> getAllMovies()
    {
        return movieRepository.findAll();
    }

    // Add Movie
    public Movie save(Movie movie)
    {
        movieValidator.validate(movie);
        movie.setTitle(movieValidator.cleanString(movie.getTitle()));
        movie.setGenre(movieValidator.cleanString(movie.getGenre()));
        return movieRepository.save(movie);
    }

    // Upgrade movie by Title
    public Movie upgradeByTitle(Movie updatedMovie, String title)
    {
        String cleanedTitle = movieValidator.cleanString(title);
        Movie movieExist = movieRepository.findByTitle(cleanedTitle).orElseThrow(() -> new NotFoundException("Movie not found with title: " + cleanedTitle));

        movieValidator.validate(updatedMovie);
        movieExist.setTitle(movieValidator.cleanString(updatedMovie.getTitle()));
        movieExist.setGenre(movieValidator.cleanString(updatedMovie.getGenre()));
        movieExist.setDuration(updatedMovie.getDuration());
        movieExist.setRating(updatedMovie.getRating());
        movieExist.setReleaseYear(updatedMovie.getReleaseYear());
        return movieRepository.save(movieExist);
    }

    // Delete movie by Title
    public void deleteByTitle(String title)
    {
        String cleanedTitle = movieValidator.cleanString(title);
        Movie movie = movieRepository.findByTitle(cleanedTitle).orElseThrow(() -> new NotFoundException("Movie not found with title: " + cleanedTitle));
        movieRepository.delete(movie);
    }
}
