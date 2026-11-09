package com.antoniosifov.popcornpalace.service;

import com.antoniosifov.popcornpalace.dto.ShowtimeDTO;
import com.antoniosifov.popcornpalace.errors.NotFoundException;
import com.antoniosifov.popcornpalace.model.Movie;
import com.antoniosifov.popcornpalace.model.Showtime;
import com.antoniosifov.popcornpalace.repository.ShowtimeRepository;
import com.antoniosifov.popcornpalace.repository.MovieRepository;
import com.antoniosifov.popcornpalace.validation.ShowtimeValidator;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.antoniosifov.popcornpalace.dto.ShowtimeDTO.convertToDTO;

@Service
public class ShowtimeService
{
    private final ShowtimeRepository showtimeRepository;
    private final MovieRepository movieRepository;
    private final ShowtimeValidator showtimeValidator;

    public ShowtimeService (ShowtimeRepository showtimeRepository, MovieRepository movieRepository, ShowtimeValidator showtimeValidator)
    {
        this.showtimeRepository = showtimeRepository;
        this.movieRepository = movieRepository;
        this.showtimeValidator = showtimeValidator;
    }

    // Get showtime by ID
    public ShowtimeDTO getShowtimeById(Long id)
    {
        Showtime showtime = showtimeRepository.findById(id).orElseThrow(() -> new NotFoundException("Showtime not found with id: " + id));
        return convertToDTO(showtime);
    }

    public List<ShowtimeDTO> getAllShowtimes()
    {
        return showtimeRepository.findAll().stream()
                .map(ShowtimeDTO::convertToDTO)
                .toList();
    }

    // Add showtime
    public Showtime save (Showtime showtime)
    {
        showtimeValidator.validate(showtime);
        Long movieId = showtime.getMovie().getId();

        Movie movie = movieRepository.findById(showtime.getMovie().getId()).orElseThrow(() -> new NotFoundException("Movie not found with ID: " + movieId));

        if (isOverlappingWithExistingShowtimes(showtime)) {
            throw new IllegalArgumentException("Showtime overlaps with an existing one in this theater");
        }

        showtime.setTheater(showtimeValidator.cleanString(showtime.getTheater()));
        showtime.setMovie(movie);
        return showtimeRepository.save(showtime);
    }

    // Upgrade showtime by ID
    public void upgradeById(Showtime updatedShowtime, Long id)
    {
        Showtime showtimeExist = showtimeRepository.findById(id).orElseThrow(() -> new NotFoundException("Showtime not found with id: " +id));
        showtimeValidator.validate(updatedShowtime);
        Long movieId = updatedShowtime.getMovie().getId();

        Movie newMovie = movieRepository.findById(movieId).orElseThrow(() -> new NotFoundException("Movie not found with ID: " + movieId));

        showtimeExist.setMovie(newMovie);
        showtimeExist.setTheater(showtimeValidator.cleanString(updatedShowtime.getTheater()));
        showtimeExist.setStart_time(updatedShowtime.getStart_time());
        showtimeExist.setEnd_time(updatedShowtime.getEnd_time());
        showtimeExist.setPrice(updatedShowtime.getPrice());
        showtimeRepository.save(showtimeExist);
    }

    // Delete showtime by ID
    public void deleteById(Long showtimeId)
    {
        Showtime showtime = showtimeRepository.findById(showtimeId).orElseThrow(() -> new NotFoundException("Showtime not found with id: " + showtimeId));
        showtimeRepository.delete(showtime);
    }

    private boolean isOverlappingWithExistingShowtimes(Showtime showtime)
    {
        return showtimeRepository.findAllByTheater(showtime.getTheater()).stream()
                .anyMatch(existing ->
                        showtime.getStart_time().isBefore(existing.getEnd_time()) &&
                                showtime.getEnd_time().isAfter(existing.getStart_time())
                );
    }
}
