package com.att.tdp.popcorn_palace.service;

import com.att.tdp.popcorn_palace.dto.ShowtimeDTO;
import com.att.tdp.popcorn_palace.errors.NotFoundException;
import com.att.tdp.popcorn_palace.model.Movie;
import com.att.tdp.popcorn_palace.model.Showtime;
import com.att.tdp.popcorn_palace.repository.MovieRepository;
import com.att.tdp.popcorn_palace.repository.ShowtimeRepository;
import com.att.tdp.popcorn_palace.validation.ShowtimeValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ShowtimeServiceTest
{
    @DisplayName("Should return all showtimes as DTOs")
    @Test
    void shouldReturnAllShowtimesAsDtos()
    {
        ShowtimeRepository showtimeRepository = mock(ShowtimeRepository.class);
        MovieRepository movieRepository = mock(MovieRepository.class);
        ShowtimeValidator showtimeValidator = new ShowtimeValidator();
        ShowtimeService showtimeService = new ShowtimeService(showtimeRepository, movieRepository, showtimeValidator);

        Movie movie = new Movie();
        movie.setId(1L);

        Showtime showtime = new Showtime();
        showtime.setId(10L);
        showtime.setMovie(movie);
        showtime.setTheater("Main Hall");
        showtime.setStart_time(LocalDateTime.of(2025, 3, 25, 18, 0));
        showtime.setEnd_time(LocalDateTime.of(2025, 3, 25, 20, 0));
        showtime.setPrice(50.0);

        when(showtimeRepository.findAll()).thenReturn(List.of(showtime));

        List<ShowtimeDTO> showtimes = showtimeService.getAllShowtimes();

        assertEquals(1, showtimes.size());
        assertEquals(10L, showtimes.getFirst().getId());
        assertEquals(1L, showtimes.getFirst().getMovieId());
        assertEquals("Main Hall", showtimes.getFirst().getTheater());
    }

    @DisplayName("Should save valid showtime")
    @Test
    void shouldSaveValidShowtime()
    {
        ShowtimeRepository showtimeRepository = mock(ShowtimeRepository.class);
        MovieRepository movieRepository = mock(MovieRepository.class);
        ShowtimeValidator showtimeValidator = new ShowtimeValidator();
        ShowtimeService showtimeService = new ShowtimeService(showtimeRepository, movieRepository, showtimeValidator);

        Movie movie = new Movie();
        movie.setId(1L);

        Showtime showtime = new Showtime();
        showtime.setMovie(movie);
        showtime.setTheater("Main Hall");
        showtime.setStart_time(LocalDateTime.of(2025, 3, 25, 18, 0));
        showtime.setEnd_time(LocalDateTime.of(2025, 3, 25, 20, 0));
        showtime.setPrice(50.0);

        when(movieRepository.findById(1L)).thenReturn(Optional.of(movie));
        when(showtimeRepository.findAllByTheater("Main Hall")).thenReturn(Collections.emptyList());
        when(showtimeRepository.save(any(Showtime.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Showtime saved = showtimeService.save(showtime);

        assertNotNull(saved);
        assertEquals("Main Hall", saved.getTheater());
        assertEquals(movie, saved.getMovie());
        assertEquals(LocalDateTime.of(2025, 3, 25, 18, 0), saved.getStart_time());
        assertEquals(LocalDateTime.of(2025, 3, 25, 20, 0), saved.getEnd_time());
        assertEquals(50.0, saved.getPrice());
        verify(showtimeRepository, times(1)).save(showtime);
    }
    @DisplayName("Should throw NotFoundException when movie not found")
    @Test
    void shouldThrowExceptionWhenMovieNotFound()
    {
        ShowtimeRepository showtimeRepository = mock(ShowtimeRepository.class);
        MovieRepository movieRepository = mock(MovieRepository.class);
        ShowtimeValidator showtimeValidator = new ShowtimeValidator();
        ShowtimeService showtimeService = new ShowtimeService(showtimeRepository, movieRepository, showtimeValidator);

        Movie movie = new Movie();
        movie.setId(42L);

        Showtime showtime = new Showtime();
        showtime.setMovie(movie);
        showtime.setTheater("Hall 1");
        showtime.setStart_time(LocalDateTime.of(2025, 3, 25, 18, 0));
        showtime.setEnd_time(LocalDateTime.of(2025, 3, 25, 20, 0));
        showtime.setPrice(50.0);

        when(movieRepository.findById(42L)).thenReturn(Optional.empty());

        NotFoundException exception = assertThrows(NotFoundException.class, () -> {
            showtimeService.save(showtime);
        });

        assertEquals("Movie not found with ID: 42", exception.getMessage());
        verify(showtimeRepository, never()).save(any());
    }
    @DisplayName("Should throw exception when showtime overlaps with existing")
    @Test
    void shouldThrowExceptionWhenOverlappingShowtime()
    {
        ShowtimeRepository showtimeRepository = mock(ShowtimeRepository.class);
        MovieRepository movieRepository = mock(MovieRepository.class);
        ShowtimeValidator showtimeValidator = new ShowtimeValidator();
        ShowtimeService showtimeService = new ShowtimeService(showtimeRepository, movieRepository, showtimeValidator);

        Movie movie = new Movie();
        movie.setId(1L);

        Showtime newShowtime = new Showtime();
        newShowtime.setMovie(movie);
        newShowtime.setTheater("Main Hall");
        newShowtime.setStart_time(LocalDateTime.of(2025, 3, 25, 18, 0));
        newShowtime.setEnd_time(LocalDateTime.of(2025, 3, 25, 20, 0));
        newShowtime.setPrice(45.0);

        Showtime existingShowtime = new Showtime();
        existingShowtime.setStart_time(LocalDateTime.of(2025, 3, 25, 19, 0));
        existingShowtime.setEnd_time(LocalDateTime.of(2025, 3, 25, 21, 0));

        when(movieRepository.findById(1L)).thenReturn(Optional.of(movie));
        when(showtimeRepository.findAllByTheater("Main Hall")).thenReturn(List.of(existingShowtime));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            showtimeService.save(newShowtime);
        });

        assertEquals("Showtime overlaps with an existing one in this theater", exception.getMessage());
        verify(showtimeRepository, never()).save(any());
    }




    @DisplayName("Should upgrade valid showtime")
    @Test
    void shouldUpgradeValidShowtime()
    {
        ShowtimeRepository showtimeRepository = mock(ShowtimeRepository.class);
        MovieRepository movieRepository = mock(MovieRepository.class);
        ShowtimeValidator showtimeValidator = new ShowtimeValidator();
        ShowtimeService showtimeService = new ShowtimeService(showtimeRepository, movieRepository, showtimeValidator);

        Long showtimeId = 1L;
        Long movieId = 10L;

        Movie existingMovie = new Movie();
        existingMovie.setId(movieId);

        Showtime existingShowtime = new Showtime();
        existingShowtime.setId(showtimeId);
        existingShowtime.setMovie(existingMovie);
        existingShowtime.setTheater("Old Hall");
        existingShowtime.setStart_time(LocalDateTime.of(2025, 3, 25, 15, 0));
        existingShowtime.setEnd_time(LocalDateTime.of(2025, 3, 25, 17, 0));
        existingShowtime.setPrice(40.0);

        Showtime updated = new Showtime();
        updated.setMovie(existingMovie);
        updated.setTheater("VIP Hall");
        updated.setStart_time(LocalDateTime.of(2025, 3, 25, 18, 0));
        updated.setEnd_time(LocalDateTime.of(2025, 3, 25, 20, 0));
        updated.setPrice(70.0);

        when(showtimeRepository.findById(showtimeId)).thenReturn(Optional.of(existingShowtime));
        when(movieRepository.findById(movieId)).thenReturn(Optional.of(existingMovie));

        showtimeService.upgradeById(updated, showtimeId);

        assertEquals("VIP Hall", existingShowtime.getTheater());
        assertEquals(LocalDateTime.of(2025, 3, 25, 18, 0), existingShowtime.getStart_time());
        assertEquals(LocalDateTime.of(2025, 3, 25, 20, 0), existingShowtime.getEnd_time());
        assertEquals(70.0, existingShowtime.getPrice());

        verify(showtimeRepository, times(1)).save(existingShowtime);
    }
    @DisplayName("Should throw exception when showtime not found for update")
    @Test
    void shouldThrowExceptionWhenShowtimeNotFoundForUpdate()
    {
        ShowtimeRepository showtimeRepository = mock(ShowtimeRepository.class);
        MovieRepository movieRepository = mock(MovieRepository.class);
        ShowtimeValidator showtimeValidator = new ShowtimeValidator();
        ShowtimeService showtimeService = new ShowtimeService(showtimeRepository, movieRepository, showtimeValidator);

        Long showtimeId = 999L;
        Showtime updatedShowtime = new Showtime();
        updatedShowtime.setMovie(new Movie());

        when(showtimeRepository.findById(showtimeId)).thenReturn(Optional.empty());

        NotFoundException exception = assertThrows(NotFoundException.class, () -> {
            showtimeService.upgradeById(updatedShowtime, showtimeId);
        });

        assertEquals("Showtime not found with id: 999", exception.getMessage());
    }
    @DisplayName("Should throw exception when movie not found during update")
    @Test
    void shouldThrowExceptionWhenMovieNotFoundDuringUpdate()
    {
        ShowtimeRepository showtimeRepository = mock(ShowtimeRepository.class);
        MovieRepository movieRepository = mock(MovieRepository.class);
        ShowtimeValidator showtimeValidator = new ShowtimeValidator();
        ShowtimeService showtimeService = new ShowtimeService(showtimeRepository, movieRepository, showtimeValidator);

        Long showtimeId = 1L;
        Long movieId = 5L;

        Showtime existingShowtime = new Showtime();
        existingShowtime.setId(showtimeId);

        Showtime updatedShowtime = new Showtime();
        Movie movie = new Movie();
        movie.setId(movieId);
        updatedShowtime.setMovie(movie);
        updatedShowtime.setTheater("Main Hall");
        updatedShowtime.setStart_time(LocalDateTime.of(2025, 3, 25, 18, 0));
        updatedShowtime.setEnd_time(LocalDateTime.of(2025, 3, 25, 20, 0));
        updatedShowtime.setPrice(50.0);

        when(showtimeRepository.findById(showtimeId)).thenReturn(Optional.of(existingShowtime));
        when(movieRepository.findById(movieId)).thenReturn(Optional.empty());

        NotFoundException exception = assertThrows(NotFoundException.class, () -> {
            showtimeService.upgradeById(updatedShowtime, showtimeId);
        });

        assertEquals("Movie not found with ID: 5", exception.getMessage());
    }


    //
    @DisplayName("Should return ShowtimeDTO if showtime exists")
    @Test
    void shouldReturnShowtimeDTOIfFound()
    {
        ShowtimeRepository showtimeRepository = mock(ShowtimeRepository.class);
        MovieRepository movieRepository = mock(MovieRepository.class);
        ShowtimeValidator showtimeValidator = new ShowtimeValidator();
        ShowtimeService showtimeService = new ShowtimeService(showtimeRepository, movieRepository, showtimeValidator);

        Long showtimeId = 1L;

        Movie movie = new Movie();
        movie.setId(1L);
        movie.setTitle("Inception");

        Showtime showtime = new Showtime();
        showtime.setId(showtimeId);
        showtime.setMovie(movie);
        showtime.setTheater("Main Hall");
        showtime.setStart_time(LocalDateTime.of(2025, 3, 25, 18, 0));
        showtime.setEnd_time(LocalDateTime.of(2025, 3, 25, 20, 0));
        showtime.setPrice(50.0);

        when(showtimeRepository.findById(showtimeId)).thenReturn(Optional.of(showtime));

        ShowtimeDTO dto = showtimeService.getShowtimeById(showtimeId);

        assertNotNull(dto);
        assertEquals(movie.getId(), dto.getMovieId());
        assertEquals("Main Hall", dto.getTheater());
        assertEquals(50.0, dto.getPrice());
    }
    @DisplayName("Should throw exception if showtime not found")
    @Test
    void shouldThrowExceptionIfShowtimeNotFound()
    {
        ShowtimeRepository showtimeRepository = mock(ShowtimeRepository.class);
        MovieRepository movieRepository = mock(MovieRepository.class);
        ShowtimeValidator showtimeValidator = new ShowtimeValidator();
        ShowtimeService showtimeService = new ShowtimeService(showtimeRepository, movieRepository, showtimeValidator);

        Long showtimeId = 999L;

        when(showtimeRepository.findById(showtimeId)).thenReturn(Optional.empty());

        NotFoundException exception = assertThrows(NotFoundException.class, () -> {
            showtimeService.getShowtimeById(showtimeId);
        });

        assertEquals("Showtime not found with id: 999", exception.getMessage());
    }


    // DELETE
    @DisplayName("Should delete showtime if found")
    @Test
    void shouldDeleteShowtimeIfFound()
    {
        ShowtimeRepository showtimeRepository = mock(ShowtimeRepository.class);
        MovieRepository movieRepository = mock(MovieRepository.class);
        ShowtimeValidator showtimeValidator = new ShowtimeValidator();
        ShowtimeService showtimeService = new ShowtimeService(showtimeRepository, movieRepository, showtimeValidator);

        Long showtimeId = 1L;
        Showtime existingShowtime = new Showtime();
        existingShowtime.setId(showtimeId);

        when(showtimeRepository.findById(showtimeId)).thenReturn(Optional.of(existingShowtime));

        showtimeService.deleteById(showtimeId);

        verify(showtimeRepository, times(1)).delete(existingShowtime);
    }
    @DisplayName("Should throw exception if showtime not found for delete")
    @Test
    void shouldThrowExceptionIfShowtimeNotFoundForDelete()
    {
        ShowtimeRepository showtimeRepository = mock(ShowtimeRepository.class);
        MovieRepository movieRepository = mock(MovieRepository.class);
        ShowtimeValidator showtimeValidator = new ShowtimeValidator();
        ShowtimeService showtimeService = new ShowtimeService(showtimeRepository, movieRepository, showtimeValidator);

        Long showtimeId = 999L;

        when(showtimeRepository.findById(showtimeId)).thenReturn(Optional.empty());

        NotFoundException exception = assertThrows(NotFoundException.class, () -> {
            showtimeService.deleteById(showtimeId);
        });

        assertEquals("Showtime not found with id: 999", exception.getMessage());
    }
}

