package com.att.tdp.popcorn_palace.service;

import com.att.tdp.popcorn_palace.errors.NotFoundException;
import com.att.tdp.popcorn_palace.model.Movie;
import com.att.tdp.popcorn_palace.repository.MovieRepository;
import com.att.tdp.popcorn_palace.validation.MovieValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


public class MovieServiceTest
{
    private MovieRepository movieRepository;
    private MovieService movieService;

    @BeforeEach
    void setUp()
    {
        movieRepository = mock(MovieRepository.class);
        MovieValidator movieValidator = new MovieValidator();
        movieService = new MovieService(movieRepository, movieValidator);
    }

    // SAVE
    @DisplayName("Should save valid movie")
    @Test
    void shouldSaveValidMovie()
    {
        MovieRepository movieRepository = mock(MovieRepository.class);
        MovieValidator movieValidator = new MovieValidator();
        MovieService movieService = new MovieService(movieRepository, movieValidator);

        Movie movie = new Movie(null, "  Inception  ", "  Sci-Fi  ", 148, 8.8, 2010, null);

        when(movieRepository.save(any(Movie.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Movie saved = movieService.save(movie);

        assertNotNull(saved);
        assertEquals("Inception", saved.getTitle()); // cleanString
        assertEquals("Sci-Fi", saved.getGenre());    // cleanString
        assertEquals(148, saved.getDuration());
        assertEquals(8.8, saved.getRating());
        assertEquals(2010, saved.getReleaseYear());

        verify(movieRepository, times(1)).save(movie);
    }
    @DisplayName("Should throw exception when movie is null")
    @Test
    void shouldThrowExceptionWhenMovieIsNull()
    {
        MovieRepository movieRepository = mock(MovieRepository.class);
        MovieValidator movieValidator = new MovieValidator();
        MovieService movieService = new MovieService(movieRepository, movieValidator);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            movieService.save(null);
        });

        assertEquals("Movie cannot be null", exception.getMessage());
        verify(movieRepository, never()).save(any());
    }


    // UPDATE
    @DisplayName("Should upgrade valid movie")
    @Test
    void shouldUpgradeValidMovie()
    {
        MovieRepository movieRepository = mock(MovieRepository.class);
        MovieValidator movieValidator = new MovieValidator();
        MovieService movieService = new MovieService(movieRepository, movieValidator);

        String originalTitle = "Inception";
        String cleanedTitle = "Inception";

        Movie existingMovie = new Movie(1L, cleanedTitle, "Sci-Fi", 148, 8.8, 2010, null);
        Movie updatedMovie = new Movie(null, "Inception", "Action", 150, 9.0, 2023, null);

        when(movieRepository.findByTitle(cleanedTitle)).thenReturn(Optional.of(existingMovie));
        when(movieRepository.save(any(Movie.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Movie result = movieService.upgradeByTitle(updatedMovie, originalTitle);

        assertEquals("Inception", result.getTitle());
        assertEquals("Action", result.getGenre());
        assertEquals(150, result.getDuration());
        assertEquals(9.0, result.getRating());
        assertEquals(2023, result.getReleaseYear());

        verify(movieRepository, times(1)).save(existingMovie);
    }
    @DisplayName("Should throw NotFoundException when movie not found for upgrade")
    @Test
    void shouldThrowExceptionWhenMovieNotFoundForUpgrade()
    {
        MovieRepository movieRepository = mock(MovieRepository.class);
        MovieValidator movieValidator = new MovieValidator();
        MovieService movieService = new MovieService(movieRepository, movieValidator);

        String title = "Unknown Movie";
        Movie updatedMovie = new Movie();

        when(movieRepository.findByTitle(title)).thenReturn(Optional.empty());

        NotFoundException exception = assertThrows(NotFoundException.class, () -> {
            movieService.upgradeByTitle(updatedMovie, title);
        });

        assertEquals("Movie not found with title: " + title, exception.getMessage());
        verify(movieRepository, never()).save(any());
    }


    // DELETE
    @DisplayName("Should delete movie by title")
    @Test
    void shouldDeleteMovieByTitle()
    {
        MovieRepository movieRepository = mock(MovieRepository.class);
        MovieValidator movieValidator = new MovieValidator();
        MovieService movieService = new MovieService(movieRepository, movieValidator);

        String title = "The Matrix";
        Movie movie = new Movie(1L, title, "Action", 136, 8.7, 1999, null);

        when(movieRepository.findByTitle(title)).thenReturn(Optional.of(movie));

        movieService.deleteByTitle(title);

        verify(movieRepository, times(1)).delete(movie);
    }
    @DisplayName("Should throw exception when movie not found for delete")
    @Test
    void shouldThrowExceptionWhenMovieNotFoundForDelete()
    {
        MovieRepository movieRepository = mock(MovieRepository.class);
        MovieValidator movieValidator = new MovieValidator();
        MovieService movieService = new MovieService(movieRepository, movieValidator);

        String title = "Unknown Movie";
        when(movieRepository.findByTitle(title)).thenReturn(Optional.empty());

        NotFoundException exception = assertThrows(NotFoundException.class, () -> {
            movieService.deleteByTitle(title);
        });

        assertEquals("Movie not found with title: Unknown Movie", exception.getMessage());
        verify(movieRepository, never()).delete(any());
    }


    // GET ALL
    @DisplayName("Should return all movies")
    @Test
    void shouldReturnAllMovies()
    {
        MovieRepository movieRepository = mock(MovieRepository.class);
        MovieValidator movieValidator = new MovieValidator();
        MovieService movieService = new MovieService(movieRepository, movieValidator);

        Movie movie1 = new Movie(1L, "Inception", "Sci-Fi", 148, 8.8, 2010, null);
        Movie movie2 = new Movie(2L, "Interstellar", "Sci-Fi", 169, 8.6, 2014, null);
        List<Movie> mockList = List.of(movie1, movie2);

        when(movieRepository.findAll()).thenReturn(mockList);

        List<Movie> result = movieService.getAllMovies();

        assertEquals(2, result.size());
        assertTrue(result.contains(movie1));
        assertTrue(result.contains(movie2));
        verify(movieRepository, times(1)).findAll();
    }
}
